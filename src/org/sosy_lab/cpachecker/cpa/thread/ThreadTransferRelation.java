/*
 *  CPAchecker is a tool for configurable software verification.
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
package org.sosy_lab.cpachecker.cpa.thread;

import static com.google.common.collect.FluentIterable.from;

import com.google.common.base.Optional;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCall;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadCreateStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CThreadOperationStatement.CThreadJoinStatement;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.model.CFAEdgeType;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionCallEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionReturnEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CFunctionSummaryStatementEdge;
import org.sosy_lab.cpachecker.cfa.model.c.CStatementEdge;
import org.sosy_lab.cpachecker.core.defaults.SingleEdgeTransferRelation;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Precision;
import org.sosy_lab.cpachecker.core.interfaces.Statistics;
import org.sosy_lab.cpachecker.cpa.thread.ThreadLabel.LabelStatus;
import org.sosy_lab.cpachecker.cpa.thread.ThreadState.SimpleThreadState;
import org.sosy_lab.cpachecker.exceptions.CPATransferException;


@Options(prefix = "cpa.thread")
public class ThreadTransferRelation extends SingleEdgeTransferRelation {
  @Option(
    secure = true,
    description = "The case when the same thread is created several times we do not support."
        + "We may skip or fail in this case.")
  private boolean skipTheSameThread = false;

  @Option(
    secure = true,
    description = "The case when the same thread is created several times we do not support."
        + "We may try to support it with self-parallelizm.")
  private boolean supportSelfCreation = false;

  @Option(secure = true, description = "Simple thread analysis from theory paper")
  private boolean simpleMode = false;

  private final ThreadCPAStatistics threadStatistics;

  public ThreadTransferRelation(Configuration pConfig) throws InvalidConfigurationException {
    pConfig.inject(this);
    threadStatistics = new ThreadCPAStatistics();
  }

  @Override
  public Collection<? extends AbstractState> getAbstractSuccessorsForEdge(AbstractState pState,
      Precision pPrecision, CFAEdge pCfaEdge) throws CPATransferException, InterruptedException {

    threadStatistics.transfer.start();
    ThreadState tState = (ThreadState)pState;
    ThreadState newState = tState;

    try {
      if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionCallEdge) {
        newState = handleFunctionCall(tState, (CFunctionCallEdge) pCfaEdge);
      } else if (pCfaEdge instanceof CFunctionSummaryStatementEdge) {
        CFunctionCall functionCall = ((CFunctionSummaryStatementEdge) pCfaEdge).getFunctionCall();
        if (isThreadCreateFunction(functionCall)) {
          newState = handleParentThread(tState, (CThreadCreateStatement) functionCall);
        }
      } else if (pCfaEdge.getEdgeType() == CFAEdgeType.StatementEdge) {
        CStatement stmnt = ((CStatementEdge) pCfaEdge).getStatement();
        if (stmnt instanceof CThreadJoinStatement) {
          threadStatistics.threadJoins.inc();
          newState = joinThread(tState, (CThreadJoinStatement) stmnt);
        }
      } else if (pCfaEdge.getEdgeType() == CFAEdgeType.FunctionReturnEdge) {
        CFunctionCall functionCall =
            ((CFunctionReturnEdge) pCfaEdge).getSummaryEdge().getExpression();
        if (isThreadCreateFunction(functionCall)) {
          newState = null;
        }
      }
      if (newState != null) {
        return Collections.singleton(newState);
      } else {
        return Collections.emptySet();
      }
    } catch (CPATransferException e) {
      if (skipTheSameThread) {
        return Collections.emptySet();
      } else {
        throw e;
      }
    } finally {
      threadStatistics.transfer.stop();
    }
  }

  private ThreadState handleFunctionCall(ThreadState state, CFunctionCallEdge pCfaEdge)
      throws CPATransferException {

    ThreadState newState = state;
    CFunctionCall fCall = pCfaEdge.getSummaryEdge().getExpression();
    if (isThreadCreateFunction(fCall)) {
      newState = handleChildThread(state, (CThreadCreateStatement) fCall);
      if (threadStatistics.createdThreads.add(pCfaEdge.getSuccessor().getFunctionName())) {
        threadStatistics.threadCreates.inc();
        // Just to statistics
        threadStatistics.maxNumberOfThreads.setNextValue(state.getThreadSize());
      }
    } else if (isThreadJoinFunction(fCall)) {
      threadStatistics.threadJoins.inc();
      newState = joinThread(state, (CThreadJoinStatement) fCall);
    }
    return newState;
  }

  private boolean isThreadCreateFunction(CFunctionCall statement) {
    return (statement instanceof CThreadCreateStatement);
  }

  private boolean isThreadJoinFunction(CFunctionCall statement) {
    return (statement instanceof CThreadJoinStatement);
  }

  public Statistics getStatistics() {
    return threadStatistics;
  }

  private ThreadState joinThread(ThreadState state, CThreadJoinStatement stmt) {
    if (simpleMode) {
      return state;
    }
    // If we found several labels for different functions
    // it means, that there are several thread created for one thread variable.
    // Not a good situation, but it is not forbidden, so join the last assigned thread
    List<ThreadLabel> tSet = state.getThreadSet();
    Optional<ThreadLabel> result =
        from(tSet).filter(l -> l.getVarName().equals(stmt.getVariableName())).last();
    // Do not self-join
    if (result.isPresent() && !result.get().isCreatedThread()) {
      List<ThreadLabel> newSet = new ArrayList<>(tSet);
      newSet.remove(result.get());
      return createState(newSet, state.getRemovedSet());
    } else {
      return null;
    }

  }

  private ThreadState handleChildThread(ThreadState state, CThreadCreateStatement tCall)
      throws CPATransferException {
    return createThread(state, tCall, LabelStatus.CREATED_THREAD);
  }

  private ThreadState handleParentThread(ThreadState state, CThreadCreateStatement tCall)
      throws CPATransferException {
    return createThread(state, tCall, LabelStatus.PARENT_THREAD);
  }

  private ThreadState createThread(ThreadState state, CThreadCreateStatement tCall, LabelStatus pParentThread)
      throws CPATransferException {
    final String pVarName = tCall.getVariableName();
    // Just to info
    final String pFunctionName =
        tCall.getFunctionCallExpression().getFunctionNameExpression().toASTString();

    List<ThreadLabel> oldTSet = state.getThreadSet();
    List<ThreadLabel> newTSet = new ArrayList<>();
    boolean isSelfParallel = false;

    for (ThreadLabel l : oldTSet) {
      if (l.getName().equals(pFunctionName)
          && l.getVarName().equals(pVarName)
          && pParentThread == LabelStatus.CREATED_THREAD) {

        if (supportSelfCreation) {
          isSelfParallel = true;
          continue;

        } else {
          throw new CPATransferException(
              "Can not create thread " + pFunctionName + ", it was already created");
        }
      } else {
        newTSet.add(l);
      }
      isSelfParallel |= l.isSelfParallel();
    }

    ThreadLabel label = new ThreadLabel(pFunctionName, pVarName, pParentThread);
    if (isSelfParallel) {
      // Can add only the same status
      label = label.toSelfParallelLabel();
    }

    if (simpleMode) {
      // Store only current creation
      newTSet.clear();
    }

    newTSet.add(label);
    return createState(newTSet, state.getRemovedSet());
  }

  private ThreadState createState(List<ThreadLabel> tSet, List<ThreadLabel> rSet) {
    if (simpleMode) {
      return new SimpleThreadState(tSet, rSet);
    } else {
      return new ThreadState(tSet, rSet);
    }
  }
}

