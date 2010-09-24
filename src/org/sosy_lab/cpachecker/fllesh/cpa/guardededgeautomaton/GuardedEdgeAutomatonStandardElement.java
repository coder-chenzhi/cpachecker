package org.sosy_lab.cpachecker.fllesh.cpa.guardededgeautomaton;

import org.sosy_lab.cpachecker.fllesh.util.Automaton;

public class GuardedEdgeAutomatonStandardElement extends GuardedEdgeAutomatonStateElement {

  public GuardedEdgeAutomatonStandardElement(Automaton.State pState, boolean pIsFinalState) {
    super(pState, pIsFinalState);
  }
  
  public GuardedEdgeAutomatonStandardElement(GuardedEdgeAutomatonPredicateElement pElement) {
    super(pElement.getAutomatonState(), pElement.isFinalState());
  }
  
  @Override
  public boolean equals(Object pOther) {
    if (this == pOther) {
      return true;
    }
    
    if (pOther == null) {
      return false;
    }
    
    if (!pOther.getClass().equals(getClass())) {
      return false;
    }
    
    GuardedEdgeAutomatonStandardElement lOther = (GuardedEdgeAutomatonStandardElement)pOther;
    
    return (lOther.isFinalState() == isFinalState()) && lOther.getAutomatonState().equals(getAutomatonState());
  }
  
  @Override
  public int hashCode() {
    return getAutomatonState().hashCode() + 37239;
  }
  
}
