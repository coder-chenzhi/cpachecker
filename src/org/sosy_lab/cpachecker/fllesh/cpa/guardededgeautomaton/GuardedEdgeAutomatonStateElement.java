package org.sosy_lab.cpachecker.fllesh.cpa.guardededgeautomaton;

import java.util.ArrayList;

import org.sosy_lab.cpachecker.fllesh.ecp.ECPGuard;
import org.sosy_lab.cpachecker.fllesh.ecp.ECPPredicate;
import org.sosy_lab.cpachecker.fllesh.ecp.translators.GuardedEdgeLabel;
import org.sosy_lab.cpachecker.fllesh.util.Automaton;

public abstract class GuardedEdgeAutomatonStateElement implements
    GuardedEdgeAutomatonElement {

  private final Automaton.State mAutomatonState;
  private final boolean mIsFinalState;
  
  public GuardedEdgeAutomatonStateElement(Automaton.State pState, boolean pIsFinalState) {
    mAutomatonState = pState;
    mIsFinalState = pIsFinalState;
  }
  
  public final boolean isFinalState() {
    return mIsFinalState;
  }
  
  public final Automaton.State getAutomatonState() {
    return mAutomatonState;
  }
  
  public static GuardedEdgeAutomatonStateElement create(Automaton<GuardedEdgeLabel>.Edge pEdge, Automaton<GuardedEdgeLabel> pAutomaton) {
    Automaton.State lAutomatonState = pEdge.getTarget();
    
    GuardedEdgeLabel lLabel = pEdge.getLabel();
    
    boolean lIsFinalState = pAutomaton.getFinalStates().contains(lAutomatonState);
    
    if (lLabel.hasGuards()) {
      ArrayList<ECPPredicate> lPredicates = new ArrayList<ECPPredicate>(lLabel.getNumberOfGuards());
      
      for (ECPGuard lGuard : lLabel) {
        assert(lGuard instanceof ECPPredicate);
        
        lPredicates.add((ECPPredicate)lGuard);
      }
      
      return new GuardedEdgeAutomatonPredicateElement(lAutomatonState, lPredicates, lIsFinalState);
    }
    else {
      return new GuardedEdgeAutomatonStandardElement(lAutomatonState, lIsFinalState);
    }
  }
  
  @Override
  public String toString() {
    return mAutomatonState.toString();
  }

}
