/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2012  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.lock;

import org.sosy_lab.common.configuration.Configuration;
import org.sosy_lab.common.configuration.InvalidConfigurationException;
import org.sosy_lab.common.configuration.Option;
import org.sosy_lab.common.configuration.Options;
import org.sosy_lab.cpachecker.cfa.blocks.Block;
import org.sosy_lab.cpachecker.cfa.model.CFANode;
import org.sosy_lab.cpachecker.cfa.model.FunctionExitNode;
import org.sosy_lab.cpachecker.core.defaults.GenericReducer;
import org.sosy_lab.cpachecker.core.defaults.SingletonPrecision;

@Options(prefix = "cpa.lock")
public class LockReducer extends GenericReducer<AbstractLockState, SingletonPrecision> {

  @Option(description = "reduce recursive locks to a single access", secure = true)
  private boolean aggressiveReduction = false;

  // Attention! Error trace may be restored incorrectly.
  // If two states with different locks are reduced to the one state,
  // the path will be always restored through the first one
  @Option(description = "reduce unused locks", secure = true)
  private boolean reduceUselessLocks = false;

  public LockReducer(Configuration config) throws InvalidConfigurationException {
    config.inject(this);
  }

  @Override
  public AbstractLockState getVariableReducedState0(
      AbstractLockState pExpandedElement, Block pContext, CFANode pCallNode) {
    AbstractLockStateBuilder builder = pExpandedElement.builder();
    builder.reduce();
    if (reduceUselessLocks) {
      builder.reduceLocks(pContext.getCapturedLocks());
    } else if (aggressiveReduction) {
      builder.reduceLockCounters(pContext.getCapturedLocks());
    }
    AbstractLockState reducedState = builder.build();
    assert getVariableExpandedState0(pExpandedElement, pContext, reducedState)
        .equals(pExpandedElement);
    return reducedState;
  }

  @Override
  public AbstractLockState getVariableExpandedState0(
      AbstractLockState pRootElement, Block pReducedContext, AbstractLockState pReducedElement) {

    AbstractLockStateBuilder builder = pReducedElement.builder();
    builder.expand(pRootElement);
    if (reduceUselessLocks) {
      builder.expandLocks((LockState) pRootElement, pReducedContext.getCapturedLocks());
    } else if (aggressiveReduction) {
      builder.expandLockCounters(pRootElement, pReducedContext.getCapturedLocks());
    }
    return builder.build();
  }

  @Override
  public SingletonPrecision getVariableReducedPrecision0(
      SingletonPrecision pPrecision, Block pContext) {
    return pPrecision;
  }

  @Override
  public SingletonPrecision getVariableExpandedPrecision0(
      SingletonPrecision pRootPrecision, Block pRootContext, SingletonPrecision pReducedPrecision) {
    return pReducedPrecision;
  }

  @Override
  public Object getHashCodeForState0(
      AbstractLockState pElementKey, SingletonPrecision pPrecisionKey) {
    return pElementKey.getHashCodeForState();
  }

  @Override
  public AbstractLockState rebuildStateAfterFunctionCall0(
      AbstractLockState pRootState,
      AbstractLockState pEntryState,
      AbstractLockState pExpandedState,
      FunctionExitNode pExitLocation) {
    return pExpandedState;
  }
}
