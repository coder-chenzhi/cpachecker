/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2018  Dirk Beyer
 *  All rights reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 *
 *  CPAchecker web page:
 *    http://cpachecker.sosy-lab.org
 */
package org.sosy_lab.cpachecker.core.algorithm;

import static com.google.common.collect.FluentIterable.from;
import static org.sosy_lab.cpachecker.util.AbstractStates.IS_TARGET_STATE;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.CFA;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.Specification;
import org.sosy_lab.cpachecker.core.counterexample.AssumptionToEdgeAllocator;
import org.sosy_lab.cpachecker.core.counterexample.CounterexampleInfo;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.ConfigurableProgramAnalysis;
import org.sosy_lab.cpachecker.core.interfaces.Property;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.core.interfaces.StatisticsProvider;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.arg.ARGCPA;
import org.sosy_lab.cpachecker.cpa.arg.ARGState;
import org.sosy_lab.cpachecker.cpa.arg.ARGUtils;
import org.sosy_lab.cpachecker.cpa.testtargets.TestTargetCPA;
import org.sosy_lab.cpachecker.cpa.testtargets.TestTargetProvider;
import org.sosy_lab.cpachecker.cpa.testtargets.TestTargetTransferRelation;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.exceptions.CounterexampleAnalysisFailed;
import org.sosy_lab.cpachecker.exceptions.InfeasibleCounterexampleException;
import org.sosy_lab.cpachecker.exceptions.RefinementFailedException;
import org.sosy_lab.cpachecker.util.CPAs;
import org.sosy_lab.cpachecker.util.Property.CommonCoverageType;
import org.sosy_lab.cpachecker.util.SpecificationProperty;
import org.sosy_lab.cpachecker.util.error.DummyErrorState;
import org.sosy_lab.cpachecker.util.testcase.TestCaseExporter;

@Options(prefix = "testcase")
public class TestCaseGeneratorAlgorithm implements Algorithm, StatisticsProvider {

  @Option(
    secure = true,
    name = "inStats",
    description = "display all test targets and non-covered test targets in statistics"
  )
  private boolean printTestTargetInfoInStats = false;

  @Option(secure = true,  description = "when generating tests covering error call stop as soon as generated one test case and report false (only possible in combination with error call property specification")
  private boolean reportCoveredErrorCallAsError = false;

  private final Algorithm algorithm;
  private final AssumptionToEdgeAllocator assumptionToEdgeAllocator;
  private final ConfigurableProgramAnalysis cpa;
  private final LogManager logger;
  private final ShutdownNotifier shutdownNotifier;
  private final Set<CFAEdge> testTargets;
  private final SpecificationProperty specProp;
  private final TestCaseExporter exporter;

  public TestCaseGeneratorAlgorithm(
      final Algorithm pAlgorithm,
      final CFA pCfa,
      final Configuration pConfig,
      final ConfigurableProgramAnalysis pCpa,
      final LogManager pLogger,
      final ShutdownNotifier pShutdownNotifier,
      final Specification pSpec)
      throws InvalidConfigurationException {
    pConfig.inject(this);
    CPAs.retrieveCPAOrFail(pCpa, ARGCPA.class, TestCaseGeneratorAlgorithm.class);
    algorithm = pAlgorithm;
    cpa = pCpa;
    logger = pLogger;
    assumptionToEdgeAllocator =
        AssumptionToEdgeAllocator.create(pConfig, logger, pCfa.getMachineModel());
    shutdownNotifier = pShutdownNotifier;
    TestTargetCPA testTargetCpa =
        CPAs.retrieveCPAOrFail(pCpa, TestTargetCPA.class, TestCaseGeneratorAlgorithm.class);
    testTargets =
        ((TestTargetTransferRelation) testTargetCpa.getTransferRelation()).getTestTargets();

    exporter = new TestCaseExporter(pCfa, logger, pConfig);

    if (pSpec.getProperties().size() == 1) {
      specProp = pSpec.getProperties().iterator().next();
      Preconditions.checkArgument(
          specProp.getProperty() instanceof CommonCoverageType,
          "Property %s not supported for test generation",
          specProp.getProperty());
    } else {
      specProp = null;
    }
  }

  @Override
  public AlgorithmStatus run(final ReachedSet pReached)
      throws CPAException, InterruptedException, CPAEnabledAnalysisPropertyViolationException {
    int uncoveredGoalsAtStart = testTargets.size();
    // clean up ARG
    if (pReached.getWaitlist().size() > 1
        || !pReached.getWaitlist().contains(pReached.getFirstState())) {
      pReached
          .getWaitlist()
          .stream()
          .filter(
              (AbstractState state) -> {
                return ((ARGState) state).getChildren().size() > 0;
              })
          .forEach(
              (AbstractState state) -> {
                ARGState argState = (ARGState) state;
                List<ARGState> removedChildren = new ArrayList<>(2);
                for (ARGState child : argState.getChildren()) {
                  if (!pReached.contains(child)) {
                    removedChildren.add(child);
                  }
                }
                for (ARGState child : removedChildren) {
                  child.removeFromARG();
                }
              });
    }

    try {
      boolean shouldReturnFalse;
      while (pReached.hasWaitingState() && !testTargets.isEmpty()) {
        shutdownNotifier.shutdownIfNecessary();
        shouldReturnFalse = false;

        assert ARGUtils.checkARG(pReached);
        assert (from(pReached).filter(IS_TARGET_STATE).isEmpty());

        AlgorithmStatus status = AlgorithmStatus.UNSOUND_AND_IMPRECISE;
        try {
          status = algorithm.run(pReached);

        } catch (CPAException e) {
          // precaution always set precision to false, thus last target state not handled in case of
          // exception
          status = status.withPrecise(false);
          logger.logUserException(Level.WARNING, e, "Analysis not completed.");
          if (!(e instanceof CounterexampleAnalysisFailed
              || e instanceof RefinementFailedException
              || e instanceof InfeasibleCounterexampleException)) {
            throw e;
          }
        } catch (InterruptedException e1) {
          // may be thrown only be counterexample check, if not will be thrown again in finally
          // block due to respective shutdown notifier call)
          status = status.withPrecise(false);
        } catch (Exception e2) {
          // precaution always set precision to false, thus last target state not handled in case of
          // exception
          status = status.withPrecise(false);
          throw e2;
        } finally {

          assert ARGUtils.checkARG(pReached);
          assert (from(pReached).filter(IS_TARGET_STATE).size() < 2);

          AbstractState reachedState = from(pReached).firstMatch(IS_TARGET_STATE).orNull();
          if (reachedState != null) {

            ARGState argState = (ARGState) reachedState;

            Collection<ARGState> parentArgStates = argState.getParents();

            assert (parentArgStates.size() == 1);

            ARGState parentArgState = parentArgStates.iterator().next();

            CFAEdge targetEdge = parentArgState.getEdgeToChild(argState);
            if (targetEdge != null) {
              if (testTargets.contains(targetEdge)) {

                if (status.isPrecise()) {
                  CounterexampleInfo cexInfo =
                      ARGUtils.tryGetOrCreateCounterexampleInformation(argState, cpa, assumptionToEdgeAllocator)
                          .get();
                  exporter.writeTestCaseFiles(cexInfo, Optional.of(specProp));

                  logger.log(Level.FINE, "Removing test target: " + targetEdge.toString());
                  testTargets.remove(targetEdge);

                  if (shouldReportCoveredErrorCallAsError()) {
                    addErrorStateWithViolatedProperty(pReached);
                    shouldReturnFalse = true;
                  }
                } else {
                  logger.log(
                      Level.FINE,
                      "Status was not precise. Current test target is not removed:"
                          + targetEdge.toString());
                }
              } else {
                logger.log(
                    Level.FINE,
                    "Found test target is not in provided set of test targets:"
                        + targetEdge.toString());
              }
            } else {
              logger.log(Level.FINE, "Target edge was null.");
            }

            argState.removeFromARG();
            pReached.remove(reachedState);
            pReached.reAddToWaitlist(parentArgState);

            assert ARGUtils.checkARG(pReached);
          } else {
            logger.log(Level.FINE, "There was no target state in the reached set.");
          }
          shutdownNotifier.shutdownIfNecessary();
        }
        if (shouldReturnFalse) {
          return AlgorithmStatus.SOUND_AND_PRECISE;
        }
      }

      cleanUpIfNoTestTargetsRemain(pReached);
    } finally {
      if (uncoveredGoalsAtStart != testTargets.size()) {
        logger.log(Level.SEVERE, TestTargetProvider.getCoverageInfo());
      }
    }

    return AlgorithmStatus.NO_PROPERTY_CHECKED;
  }

  private void cleanUpIfNoTestTargetsRemain(final ReachedSet pReached) {
    if (testTargets.isEmpty()) {
      List<AbstractState> waitlist = new ArrayList<>(pReached.getWaitlist());
      for (AbstractState state : waitlist) {
        pReached.removeOnlyFromWaitlist(state);
      }
    }
  }

  private void addErrorStateWithViolatedProperty(final ReachedSet pReached) {
    Preconditions.checkState(shouldReportCoveredErrorCallAsError());
    pReached.add(
        new DummyErrorState(pReached.getLastState()) {
          private static final long serialVersionUID = 5522643115974481914L;

          @Override
          public Set<Property> getViolatedProperties() throws IllegalStateException {
            return ImmutableSet.of(
                new Property() {
                  @Override
                  public String toString() {
                    return specProp.getProperty().toString();
                  }
                });
          }
        },
        SingletonPrecision.getInstance());
  }

  private boolean shouldReportCoveredErrorCallAsError() {
    return reportCoveredErrorCallAsError
        && specProp != null
        && specProp.getProperty().equals(CommonCoverageType.COVERAGE_ERROR);
  }


  @Override
  public void collectStatistics(final Collection<Statistics> pStatsCollection) {
    pStatsCollection.add(TestTargetProvider.getTestTargetStatisitics(printTestTargetInfoInStats));
  }
}
