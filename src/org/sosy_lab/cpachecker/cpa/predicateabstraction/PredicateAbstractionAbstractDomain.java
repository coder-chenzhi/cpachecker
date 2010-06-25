/*
 *  CPAchecker is a tool for configurable software verification.
 *  This file is part of CPAchecker.
 *
 *  Copyright (C) 2007-2010  Dirk Beyer
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
package org.sosy_lab.cpachecker.cpa.predicateabstraction;

import org.sosy_lab.cpachecker.util.symbpredabstraction.interfaces.AbstractFormulaManager;
import org.sosy_lab.cpachecker.core.interfaces.AbstractDomain;
import org.sosy_lab.cpachecker.core.interfaces.AbstractElement;
import org.sosy_lab.cpachecker.core.interfaces.JoinOperator;
import org.sosy_lab.cpachecker.core.interfaces.PartialOrder;
import org.sosy_lab.cpachecker.exceptions.CPAException;

/**
 * AbstractDomain for explicit-state lazy abstraction.
 *
 * @author Alberto Griggio <alberto.griggio@disi.unitn.it>
 */
public class PredicateAbstractionAbstractDomain implements AbstractDomain {

  private PredicateAbstractionCPA cpa;

  public PredicateAbstractionAbstractDomain(PredicateAbstractionCPA cpa) {
    this.cpa = cpa;
  }

  private final static class ExplicitBottomElement extends PredicateAbstractionAbstractElement {
    public ExplicitBottomElement () {
      super(null);
    }
    @Override
    public String toString() { return "<BOTTOM>"; }
  }
  private final static class ExplicitTopElement extends PredicateAbstractionAbstractElement {
    public ExplicitTopElement () {
      super(null);
    }
  }

  private final static class ExplicitJoinOperator implements JoinOperator {
    @Override
    public AbstractElement join(AbstractElement element1,
        AbstractElement element2) throws CPAException {
      return top;
    }
  }

  private final class ExplicitPartialOrder implements PartialOrder {
    @Override
    public boolean satisfiesPartialOrder(AbstractElement element1,
        AbstractElement element2) throws CPAException {
      PredicateAbstractionAbstractElement e1 = (PredicateAbstractionAbstractElement)element1;
      PredicateAbstractionAbstractElement e2 = (PredicateAbstractionAbstractElement)element2;

      if (e1 == bottom) {
        return true;
      } else if (e2 == top) {
        return true;
      } else if (e2 == bottom) {
        return false;
      } else if (e1 == top) {
        return false;
      }

      assert(e1.getAbstraction() != null);
      assert(e2.getAbstraction() != null);

      AbstractFormulaManager amgr = cpa.getAbstractFormulaManager();
      return amgr.entails(e1.getAbstraction(), e2.getAbstraction());
    }
  }

  private final static ExplicitBottomElement bottom = new ExplicitBottomElement();
  private final static ExplicitTopElement top = new ExplicitTopElement();
  private final static JoinOperator join = new ExplicitJoinOperator();
  private final PartialOrder partial = new ExplicitPartialOrder();

  @Override
  public AbstractElement getBottomElement() {
    return bottom;
  }

  @Override
  public JoinOperator getJoinOperator() {
    return join;
  }

  @Override
  public PartialOrder getPartialOrder() {
    return partial;
  }

  @Override
  public AbstractElement getTopElement() {
    return top;
  }

  public PredicateAbstractionCPA getCPA() {
    return cpa;
  }

}
