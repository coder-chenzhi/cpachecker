package org.sosy_lab.cpachecker.core.algorithm.acsl;

import org.sosy_lab.cpachecker.cfa.ast.c.CExpression;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;

public class ACSLUnaryTerm implements ACSLTerm {

  private final ACSLTerm term;
  private final UnaryOperator operator;

  public ACSLUnaryTerm(ACSLTerm pTerm, UnaryOperator op) {
    term = pTerm;
    operator = op;
  }

  @Override
  public String toString() {
    if (operator.equals(UnaryOperator.SIZEOF)) {
      return operator.toString() + "(" + term.toString() + ")";
    }
    return operator.toString() + term.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (o instanceof ACSLUnaryTerm) {
      ACSLUnaryTerm other = (ACSLUnaryTerm) o;
      return term.equals(other.term) && operator.equals(other.operator);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return 7 * term.hashCode() + operator.hashCode();
  }

  public ACSLTerm getInnerTerm() {
    return term;
  }

  public UnaryOperator getOperator() {
    return operator;
  }

  @Override
  public CExpression accept(ACSLTermToCExpressionVisitor visitor) throws UnrecognizedCodeException {
    return visitor.visit(this);
  }

  @Override
  public ACSLTerm useOldValues() {
    return new ACSLUnaryTerm(term.useOldValues(), operator);
  }

  @Override
  public boolean isAllowedIn(Class<?> clauseType) {
    return term.isAllowedIn(clauseType);
  }
}
