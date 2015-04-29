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
package org.sosy_lab.cpachecker.util.ci.translators;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.sosy_lab.common.Pair;
import org.sosy_lab.cpachecker.core.interfaces.AbstractState;
import org.sosy_lab.cpachecker.exceptions.CPAException;
import org.sosy_lab.cpachecker.util.AbstractStates;
import org.sosy_lab.cpachecker.util.predicates.pathformula.SSAMap;


public abstract class AbstractRequirementsTranslator<T extends AbstractState> {

  private final Class<T> abstractStateClass;

  public AbstractRequirementsTranslator(final Class<T> pAbstractStateClass) {
    abstractStateClass = pAbstractStateClass;
  }

  protected T extractRequirement(final AbstractState pState) {
    return AbstractStates.extractStateByType(pState, abstractStateClass);
  }

  protected Collection<T> extractRequirements(final Collection<AbstractState> pStates) {
    Collection<T> requirements = new ArrayList<>();
    for (AbstractState state : pStates) {
      requirements.add(extractRequirement(state));
    }
    return requirements;
  }

  protected abstract Pair<List<String>, String> convertToFormula(final T requirement, final SSAMap indices)
      throws CPAException;

  private Pair<Pair<List<String>, String>, Pair<List<String>, String>> convertRequirements(
      final AbstractState pre, final Collection<AbstractState> post, final SSAMap postIndices,
      final int index) throws CPAException {

    Pair<List<String>, String> formulaPre = convertToFormula(extractRequirement(pre), SSAMap.emptySSAMap());
    formulaPre = Pair.of(formulaPre.getFirst(), renameDefine(formulaPre.getSecond(), ("pre" + index)));

    List<String> list = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    int BracketCounter = 0;
    int amount = post.size();

    for (AbstractState state : post){
      Pair<List<String>, String> formula = convertToFormula(extractRequirement(state), postIndices);
      list.addAll(formula.getFirst());

      if (BracketCounter != amount-1) {
        sb.append("(or ");
        BracketCounter++;
      }
      String definition = formula.getSecond().substring(formula.getSecond().indexOf("(", 1)+1);
      sb.append(definition);
    }
    for (int i=0; i<BracketCounter; i++) {
      sb.append(")");
    }

    return Pair.of(formulaPre, Pair.of(list, sb.toString()));
  }

  public static String renameDefine(final String define, final String newName) {
    int start = define.indexOf(" ") + 1;
    int end = define.indexOf(" ", start);
    return define.substring(0, start) + newName + define.substring(end);
  }
}
