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
package org.sosy_lab.cpachecker.cpa.propertyscope;

import org.sosy_lab.common.collect.PersistentList;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.core.interfaces.Graphable;
import org.sosy_lab.cpachecker.cpa.automaton.Automaton;
import org.sosy_lab.cpachecker.cpa.automaton.AutomatonState;
import org.sosy_lab.cpachecker.util.predicates.AbstractionFormula;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

class PropertyScopeState implements AbstractState, Graphable {

  private final long propertyDependantMatches;
  private final PersistentList<PropertyScopeState> prevBlockStates;
  private final CFAEdge enteringEdge;
  private final List<String> callstack;
  private final Set<ScopeLocation> scopeLocations;
  private final Optional<PropertyScopeState> prevState;
  private final Optional<AbstractionFormula> absFormula;
  private final Map<Automaton, AutomatonState> automatonStates;

  public PropertyScopeState(
      PersistentList<PropertyScopeState> pPrevBlockStates,
      long pPropertyDependantMatches,
      CFAEdge pEnteringEdge,
      List<String> pCallstack,
      Set<ScopeLocation> pScopeLocations,
      Optional<PropertyScopeState> pPrevState,
      Optional<AbstractionFormula> pAbsFormula,
      Map<Automaton, AutomatonState> pAutomatonStates) {

    prevBlockStates = pPrevBlockStates;
    propertyDependantMatches = pPropertyDependantMatches;
    enteringEdge = pEnteringEdge;
    callstack = Collections.unmodifiableList(pCallstack);
    scopeLocations = Collections.unmodifiableSet(pScopeLocations);
    prevState = pPrevState;
    absFormula = pAbsFormula;
    automatonStates = Collections.unmodifiableMap(pAutomatonStates);
  }



  public PersistentList<PropertyScopeState> getPrevBlockStates() {
    return prevBlockStates;
  }

  public CFAEdge getEnteringEdge() {
    return enteringEdge;
  }

  public List<String> getCallstack() {
    return callstack;
  }

  public long getPropertyDependantMatches() {
    return propertyDependantMatches;
  }

  public Set<ScopeLocation> getScopeLocations() {
    return scopeLocations;
  }

  public Optional<PropertyScopeState> getPrevState() {
    return prevState;
  }

  public Optional<AbstractionFormula> getAbsFormula() {
    return absFormula;
  }

  public Map<Automaton, AutomatonState> getAutomatonStates() {
    return automatonStates;
  }

  @Override
  public String toDOTLabel() {
    return scopeLocations.isEmpty()? "" : String.format("SCOPE %s", scopeLocations);

  }

  @Override
  public boolean shouldBeHighlighted() {
    return false;
  }
}
