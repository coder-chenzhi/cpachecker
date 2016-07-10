/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2015  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.smg.refiner;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;

import org.sosy_lab.common.ShutdownNotifier;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath.PathIterator;
import org.sosy_lab.cpachecker.cpa.arg.ARGPath.PathPosition;
import org.sosy_lab.cpachecker.cpa.smg.SMGAbstractionBlock;
import org.sosy_lab.cpachecker.cpa.smg.SMGAbstractionCandidate;
import org.sosy_lab.cpachecker.cpa.smg.SMGEdgeHasValue;
import org.sosy_lab.cpachecker.cpa.smg.SMGState;
import org.sosy_lab.cpachecker.exceptions.CPAException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class SMGEdgeInterpolator {

  /**
   * the postOperator relation in use
   */
  private final SMGStrongestPostOperator postOperator;

  /**
   * the precision for the Feasability check
   */
  private final SMGPrecision strongPrecision;

  /**
   * the precision in use
   */
  private final SMGPrecision originalPrecision;

  private final SMGInterpolantManager interpolantManager;

  /**
   * the error path checker to be used for feasibility checks
   */
  private final SMGFeasibilityChecker checker;

  /**
   * the number of interpolations
   */
  private int numberOfInterpolationQueries = 0;

  private final SMGState initialState;

  /**
   * the shutdownNotifier in use
   */
  private final ShutdownNotifier shutdownNotifier;

  public SMGEdgeInterpolator(SMGFeasibilityChecker pFeasibilityChecker,
      SMGStrongestPostOperator pStrongestPostOperator, SMGInterpolantManager pInterpolantManager,
      SMGState pInitialState, ShutdownNotifier pShutdownNotifier,
      SMGPrecision pOriginalPrecision, LogManager pLogger) {

    checker = pFeasibilityChecker;
    postOperator = pStrongestPostOperator;
    interpolantManager = pInterpolantManager;
    initialState = pInitialState;

    strongPrecision = SMGPrecision.createStaticPrecision(false, pLogger);
    originalPrecision = pOriginalPrecision;
    shutdownNotifier = pShutdownNotifier;
  }

  public List<SMGInterpolant> deriveInterpolant(CFAEdge pCurrentEdge,
      PathPosition pOffset, SMGInterpolant pInputInterpolant) throws CPAException, InterruptedException {
    numberOfInterpolationQueries = 0;

    // create initial state, based on input interpolant, and create initial successor by consuming
    // the next edge
    CFAEdge currentEdge = pCurrentEdge;
    List<SMGState> initialStates = pInputInterpolant.reconstructStates();

    if (currentEdge == null) {
      PathIterator it = pOffset.fullPathIterator();
      Collection<SMGState> intermediate = initialStates;

      while(intermediate.isEmpty() || !it.isPositionWithState()) {

        Collection<SMGState> newIntermediate = new ArrayList<>();

        for (SMGState state : intermediate) {
          Collection<SMGState> result =
              getInitialSuccessor(state, it.getOutgoingEdge());
          newIntermediate.addAll(result);
        }

        intermediate = newIntermediate;
        it.advance();
      }
      initialStates = new ArrayList<>(intermediate);
      currentEdge = it.getOutgoingEdge();

      if (initialStates.isEmpty()) {
        List<SMGInterpolant> resultingInterpolants = new ArrayList<>(1);
        resultingInterpolants.add(interpolantManager.getFalseInterpolant());
        return resultingInterpolants;
      }
    }

    List<SMGState> successors;
    Set<SMGAbstractionBlock> abstractionBlocks;

    successors = new ArrayList<>();
    abstractionBlocks = ImmutableSet.of();

    for (SMGState state : initialStates) {
      successors.addAll(getInitialSuccessor(state, currentEdge));
    }

    List<SMGInterpolant> resultingInterpolants;

    if (successors.isEmpty()) {
      resultingInterpolants = new ArrayList<>(1);
      resultingInterpolants.add(interpolantManager.getFalseInterpolant());
      return resultingInterpolants;
    } else {
      resultingInterpolants = new ArrayList<>(successors.size());
    }

    boolean onlySuccessor = successors.size() == 1;

    // if initial state and successor are equal, return the input interpolant
    // in general, this returned interpolant might be stronger than needed, but only in very rare
    // cases, the weaker interpolant would be different from the input interpolant, so we spare the
    // effort
    if (onlySuccessor
        && initialState.equals(Iterables.getOnlyElement(successors))
        && !originalPrecision.allowsHeapAbstractionOnNode(currentEdge.getPredecessor())) {
      resultingInterpolants.add(pInputInterpolant);
      return resultingInterpolants;
    }

    // if the current edge just changes the names of variables
    // (e.g. function arguments, returned variables)
    // then return the input interpolant with those renamings
    if (onlySuccessor && isOnlyVariableRenamingEdge(pCurrentEdge)
        && !originalPrecision.allowsHeapAbstractionOnNode(currentEdge.getPredecessor())) {
      SMGInterpolant interpolant =
          interpolantManager.createInterpolant(Iterables.getOnlyElement(successors));
      resultingInterpolants.add(interpolant);
      return resultingInterpolants;
    }

    ARGPath remainingErrorPath = pOffset.iterator().getSuffixExclusive();

    // if the remaining path, i.e., the suffix, is contradicting by itself, then return the TRUE
    // interpolant
    if (pInputInterpolant.isTrue()
        && !isTrivialToInterpolate(successors)
        && isSuffixContradicting(remainingErrorPath)) {
      SMGInterpolant interpolant =
          interpolantManager.getTrueInterpolant(successors.iterator().next().createInterpolant());
      resultingInterpolants.add(interpolant);
      return resultingInterpolants;
    }

    /*First, check with no abstraction allowed.*/

    for(SMGState initialSuccessor : successors) {
      for (SMGEdgeHasValue currentHveEdge : initialSuccessor.getHVEdges()) {
        shutdownNotifier.shutdownIfNecessary();

        // temporarily remove the hve edge of the current memory path from the candidate
        // interpolant
        initialSuccessor.forget(currentHveEdge);

        // check if the remaining path now becomes feasible
        if (isRemainingPathFeasible(remainingErrorPath, initialSuccessor)) {
          initialSuccessor.remember(currentHveEdge);
        }
      }

      /*Second, check which abstraction should be allowed.*/
      if (originalPrecision.allowsHeapAbstractionOnNode(currentEdge.getPredecessor())) {
        abstractionBlocks = interpolateAbstractions(initialSuccessor, remainingErrorPath);
      }

      SMGInterpolant result = interpolantManager.createInterpolant(initialSuccessor, abstractionBlocks);
      resultingInterpolants.add(result);
    }

    return resultingInterpolants;
  }

  private Set<SMGAbstractionBlock> interpolateAbstractions(SMGState pInitialSuccessor,
      ARGPath remainingErrorPath) throws CPAException, InterruptedException {
    SMGState abstractionTest = new SMGState(pInitialSuccessor);
    Set<SMGAbstractionBlock> result = new HashSet<>();
    SMGAbstractionCandidate candidate = abstractionTest.executeHeapAbstractionOneStep(result);

    while (!candidate.isEmpty()) {

      if (isRemainingPathFeasible(remainingErrorPath, abstractionTest)) {
        result.add(candidate.createAbstractionBlock(pInitialSuccessor));
        abstractionTest = new SMGState(pInitialSuccessor);
      } else {
        pInitialSuccessor.executeHeapAbstractionOneStep(result);
      }

      candidate = abstractionTest.executeHeapAbstractionOneStep(result);
    }

    return result;
  }

  private Collection<SMGState> getInitialSuccessor(SMGState pState, CFAEdge pCurrentEdge)
      throws CPAException, InterruptedException {
    return getInitialSuccessor(pState, pCurrentEdge, strongPrecision);
  }

  private boolean isTrivialToInterpolate(Collection<SMGState> pSuccessors) {
    return pSuccessors.size() == 1 && Iterables.getOnlyElement(pSuccessors).sizeOfHveEdges() < 1;
  }

  /**
   * This method gets the initial successor, i.e. the state following the initial state.
   *
   * @param pInitialState the initial state, i.e. the state represented by the input interpolant.
   * @param pInitialEdge the initial edge of the error path
   * @return the initial successor
   */
  private Collection<SMGState> getInitialSuccessor(
      final SMGState pInitialState,
      final CFAEdge pInitialEdge,
      final SMGPrecision precision
  ) throws CPAException, InterruptedException {

    SMGState oldState = pInitialState;

    return postOperator.getStrongestPost(oldState, precision, pInitialEdge);
  }

  /**
   * This method checks, if the given edge is only renaming variables.
   *
   * @param cfaEdge the CFA edge to check
   * @return true, if the given edge is only renaming variables
   */
  private boolean isOnlyVariableRenamingEdge(CFAEdge cfaEdge) {
    return
    // if the edge is null this is a dynamic multi edge
    cfaEdge != null

        // renames from calledFn::___cpa_temp_result_var_ to callerFN::assignedVar
        // if the former is relevant, so is the latter
        && cfaEdge.getEdgeType() == CFAEdgeType.FunctionReturnEdge

    // for the next two edge types this would also work, but variables
    // from the calling/returning function would be added to interpolant
    // as they are not "cleaned up" by the transfer relation
    // so these two stay out for now

    //|| cfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge
    //|| cfaEdge.getEdgeType() == CFAEdgeType.ReturnStatementEdge
    ;
  }

  /**
   * This method checks, if the given error path is contradicting in itself.
   *
   * @param errorPath the error path to check.
   * @return true, if the given error path is contradicting in itself, else false
   */
  private boolean isSuffixContradicting(ARGPath errorPath)
      throws CPAException, InterruptedException {
    return !isRemainingPathFeasible(errorPath, initialState);
  }

  /**
   * This method checks, whether or not the (remaining) error path is feasible when starting with
   * the given (pseudo) initial state.
   *
   * @param remainingErrorPath the error path to check feasibility on
   * @param state the (pseudo) initial state
   * @return true, it the path is feasible, else false
   */
  public boolean isRemainingPathFeasible(ARGPath remainingErrorPath, SMGState state)
      throws CPAException, InterruptedException {
    numberOfInterpolationQueries++;
    return checker.isFeasible(remainingErrorPath, state);
  }

  public int getNumberOfInterpolationQueries() {
    return numberOfInterpolationQueries;
  }
}