// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2007-2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0
package org.sosy_lab.cpachecker.core.algorithm.legion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.core.algorithm.Algorithm;
import org.sosy_lab.cpachecker.core.reachedset.ReachedSet;
import org.sosy_lab.cpachecker.cpa.value.NondeterministicValueProvider;
import org.sosy_lab.cpachecker.cpa.value.type.Value;
import org.sosy_lab.cpachecker.exceptions.CPAEnabledAnalysisPropertyViolationException;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.statistics.StatKind;
import org.sosy_lab.cpachecker.util.statistics.StatTimer;
import org.sosy_lab.cpachecker.util.statistics.StatisticsWriter;
import org.sosy_lab.java_smt.api.Model.ValueAssignment;

@Options(prefix = "legion")
public class Fuzzer {

  @Option(
      secure = true,
      description = "How many passes to fuzz before asking the solver for the first time.")
  private int initialPasses = 3;

  @Option(secure = true, description = "fuzzingPasses = ⌈ fuzzingMultiplier * fuzzingSolutions ⌉")
  private double fuzzingMultiplier = 1;

  @Option(secure = true, description = "If 0 fuzzing would run, instead run this amount of passes.")
  private int emergencyFuzzingPasses = 1;

  private final LogManager logger;
  private final TestcaseWriter outputWriter;
  private final ShutdownNotifier shutdownNotifier;
  private final NondeterministicValueProvider nonDetValueProvider;
  private int passes;
  private final StatTimer iterationTimer;

  public Fuzzer(
      String pName,
      final LogManager pLogger,
      TestcaseWriter pOutputWriter,
      ShutdownNotifier pShutdownNotifier,
      NondeterministicValueProvider pNonDetValueProvider,
      Configuration pConfig)
      throws InvalidConfigurationException {

    pConfig.inject(this, Fuzzer.class);
    this.logger = pLogger;
    this.shutdownNotifier = pShutdownNotifier;

    this.nonDetValueProvider = pNonDetValueProvider;

    this.outputWriter = pOutputWriter;
    this.passes = initialPasses;
    this.iterationTimer = new StatTimer(StatKind.SUM, "Iteration time " + pName);
  }

  /**
   * Run the fuzzing phase using pAlgorithm pPasses times on the states in pReachedSet.
   *
   * <p>Runs the Algorithm on the pReachedSet as a fuzzer. pPreloadedValues are used where
   * applicable.
   */
  public ReachedSet fuzz(
      ReachedSet pReachedSet, Algorithm pAlgorithm, List<List<ValueAssignment>> pPreLoadedValues)
      throws CPAEnabledAnalysisPropertyViolationException, CPAException, InterruptedException,
          IOException {

    for (int i = 0; i < this.passes; i++) {
      this.iterationTimer.start();
      logger.log(Level.FINE, "Fuzzing pass", i + 1);

      // Preload values if they exist
      int size = pPreLoadedValues.size();
      if (size > 0) {
        int j = i % size;
        logger.log(Level.FINER, "pPreLoadedValues at", j, "/", size);
        preloadValues(pPreLoadedValues.get(j));
      }
      try {
        // Run algorithm and collect result
        pAlgorithm.run(pReachedSet);
      } finally {
        this.outputWriter.writeTestCases(pReachedSet);
      }

      // Check whether to shut down
      shutdownNotifier.shutdownIfNecessary();

      // Otherwise, start from the beginning again
      pReachedSet.reAddToWaitlist(pReachedSet.getFirstState());
      this.iterationTimer.stop();
    }
    return pReachedSet;
  }

  public void setPasses(int pPasses) {
    this.passes = pPasses;
  }

  public void computePasses(int pPreloadedValuesSize) {
    int fuzzingPasses = (int) Math.ceil(fuzzingMultiplier * pPreloadedValuesSize);
    if (fuzzingPasses == 0) {
      fuzzingPasses = this.emergencyFuzzingPasses;
    }
    this.passes = fuzzingPasses;
  }

  /** Use assignments to preload the ValueCPA in order to use them as applicable. */
  private List<Value> preloadValues(List<ValueAssignment> assignments) {
    List<Value> values = new ArrayList<>();
    for (ValueAssignment a : assignments) {
      values.add(Value.of(a.getValue()));
    }

    this.nonDetValueProvider.setKnownValues(values);

    return values;
  }

  public void printStatistics(StatisticsWriter writer) {
    writer.put(this.iterationTimer);
    writer.put("Fuzzing Iterations", this.iterationTimer.getUpdateCount());
  }
}