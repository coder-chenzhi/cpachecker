/*
 * CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2016  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.automaton;

import com.google.common.collect.FluentIterable;
import java.util.List;
import java.util.Objects;
import javax.annotation.Nullable;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.cpa.summary.interfaces.Summary;
import org.sosy_lab.cpachecker.cpa.summary.interfaces.SummaryManager;
import org.sosy_lab.cpachecker.exceptions.CPAException;

/**
 * Summary manager for automaton CPA.
 *
 * // todo: what IS the summarization relation for the automaton?
 * basically it's the memoization of the transition.
 * seems like we are reimplementing again the logic already found in BAM..
 */
public class AutomatonSummaryManager implements SummaryManager {

  // todo: might make more sense if
  // we do the dumbest implementation first?..
  // this is annoying without the base implementation
  // (make summary a base class?...)

  @Override
  public AbstractState getAbstractSuccessorForSummary(
      AbstractState pFunctionCallState,
      Precision pFunctionCallPrecision,
      List<Summary> pSummaries,
      Block pBlock) throws CPAException, InterruptedException {

    // todo: what do we do with the summaries here?
    // calculate the postconditions from the states at function exit?
    // or assume if there was a violation it was already recorded by now?
    // todo: confusing.
    return pFunctionCallState;
  }

  @Override
  public List<? extends Summary> generateSummaries(
      AbstractState pCallState,
      Precision pEntryPrecision,
      List<? extends AbstractState> pReturnStates,
      List<Precision> pReturnPrecisions,
      CFANode pEntryNode,
      Block pBlock) {

    AutomatonState aCallState = (AutomatonState) pCallState;
    return FluentIterable.from(pReturnStates)
        .filter(AutomatonState.class)
        .transform(r -> new AutomatonSummary(aCallState, r)).toList();
  }

  @Override
  public AbstractState getWeakenedCallState(
      AbstractState pState, Precision pPrecision, Block pBlock) {
    return pState;
  }

  @Override
  public AbstractState projectToCallsite(Summary pSummary) {
    AutomatonSummary aSummary = (AutomatonSummary) pSummary;
    return aSummary.getEntryState();
  }

  @Override
  public AbstractState projectToPostcondition(Summary pSummary) {
    AutomatonSummary aSummary = (AutomatonSummary) pSummary;
    return aSummary.getExitState();
  }

  @Override
  public Summary merge(
      Summary pSummary1, Summary pSummary2) throws CPAException, InterruptedException {

    // States are not joined.
    return pSummary2;
  }

  // todo: why not have the same summary class for everything then?

  private static class AutomatonSummary implements Summary {

    // todo: the automaton states at function entry and exit.
    private final AutomatonState entryState;
    private final AutomatonState exitState;

    private AutomatonSummary(AutomatonState pEntryState, AutomatonState pExitState) {
      entryState = pEntryState;
      exitState = pExitState;
    }

    public AutomatonState getEntryState() {
      return entryState;
    }

    public AutomatonState getExitState() {
      return exitState;
    }

    @Override
    public boolean equals(@Nullable Object pO) {
      if (this == pO) {
        return true;
      }
      if (pO == null || getClass() != pO.getClass()) {
        return false;
      }
      AutomatonSummary that = (AutomatonSummary) pO;
      return Objects.equals(entryState, that.entryState) &&
          Objects.equals(exitState, that.exitState);
    }

    @Override
    public int hashCode() {
      return Objects.hash(entryState, exitState);
    }

    @Override
    public String toString() {
      return "AutomatonSummary{" +
          "entryState=" + entryState +
          ", exitState=" + exitState +
          '}';
    }
  }
}
