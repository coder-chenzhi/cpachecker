// This file is part of CPAchecker,
// a tool for configurable software verification:
// https://cpachecker.sosy-lab.org
//
// SPDX-FileCopyrightText: 2020 Dirk Beyer <https://www.sosy-lab.org>
//
// SPDX-License-Identifier: Apache-2.0

package org.sosy_lab.cpachecker.cpa.numeric.visitor;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.Collection;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.sosy_lab.common.log.LogManager;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CExpressionStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallAssignmentStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionCallStatement;
import org.sosy_lab.cpachecker.cfa.ast.c.CFunctionDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CIdExpression;
import org.sosy_lab.cpachecker.cfa.ast.c.CSimpleDeclaration;
import org.sosy_lab.cpachecker.cfa.ast.c.CStatementVisitor;
import org.sosy_lab.cpachecker.cfa.model.CFAEdge;
import org.sosy_lab.cpachecker.cfa.types.c.CSimpleType;
import org.sosy_lab.cpachecker.cfa.types.c.CType;
import org.sosy_lab.cpachecker.core.defaults.precision.VariableTrackingPrecision;
import org.sosy_lab.cpachecker.cpa.numeric.NumericState;
import org.sosy_lab.cpachecker.cpa.numeric.NumericVariable;
import org.sosy_lab.cpachecker.exceptions.UnrecognizedCodeException;
import org.sosy_lab.numericdomains.Value.NewVariableValue;
import org.sosy_lab.numericdomains.coefficients.Interval;
import org.sosy_lab.numericdomains.constraint.tree.ConstantTreeNode;
import org.sosy_lab.numericdomains.constraint.tree.TreeNode;
import org.sosy_lab.numericdomains.constraint.tree.VariableTreeNode;
import org.sosy_lab.numericdomains.environment.Variable;

public class NumericStatementVisitor
    implements CStatementVisitor<Collection<NumericState>, UnrecognizedCodeException> {

  private final NumericState state;

  /**
   * Variable holding the return value of the function.
   *
   * <p>Only necessary if the statement is a function call statement.
   */
  private final Optional<Variable> returnVariable;

  private final LogManager logger;

  private final VariableTrackingPrecision precision;

  private final CFAEdge edge;

  /**
   * Creates a NumericStatementVisitor.
   *
   * @param pState current state
   * @param cfaEdge current edge
   * @param pPrecision precision of the CPA
   */
  public NumericStatementVisitor(
      NumericState pState,
      CFAEdge cfaEdge,
      VariableTrackingPrecision pPrecision,
      LogManager logManager) {
    state = pState;
    returnVariable = Optional.empty();
    logger = logManager;
    edge = cfaEdge;
    precision = pPrecision;
  }

  /**
   * Returns a Numeric statement visitor for the {@link NumericState}.
   *
   * <p>The return variable should only be set for {@link CFunctionCallAssignmentStatement}s. For
   * other statements, the value is ignored.
   *
   * @param pState current state
   * @param pReturnVariable return variable used for a CFunctionCallAssignmentStatement
   * @param cfaEdge current edge
   * @param pPrecision precision of the CPA
   */
  public NumericStatementVisitor(
      NumericState pState,
      @Nullable Variable pReturnVariable,
      CFAEdge cfaEdge,
      VariableTrackingPrecision pPrecision,
      LogManager logManager) {
    state = pState;
    returnVariable = Optional.ofNullable(pReturnVariable);
    logger = logManager;
    edge = cfaEdge;
    precision = pPrecision;
  }

  @Override
  public Collection<NumericState> visit(CExpressionStatement pIastExpressionStatement)
      throws UnrecognizedCodeException {
    return ImmutableSet.of(state.createCopy());
  }

  @Override
  public Collection<NumericState> visit(
      CExpressionAssignmentStatement pIastExpressionAssignmentStatement)
      throws UnrecognizedCodeException {
    if (!(pIastExpressionAssignmentStatement.getLeftHandSide() instanceof CIdExpression)) {
      return ImmutableSet.of(state.createCopy());
    }

    CSimpleDeclaration declaration =
        ((CIdExpression) pIastExpressionAssignmentStatement.getLeftHandSide()).getDeclaration();

    // Create variable to which the value is assigned
    Optional<NumericVariable> variable =
        NumericVariable.valueOf(
            declaration, edge.getSuccessor(), precision, state.getManager(), logger);
    if (variable.isEmpty()) {
      return ImmutableSet.of(state.createCopy());
    }

    final NumericState extendedState;
    if (state.getValue().getEnvironment().containsVariable(variable.get())) {
      extendedState = state.createCopy();
    } else {
      Collection<Variable> newVariable = ImmutableSet.of(variable.get());
      if (variable.get().getSimpleType().getType().isIntegerType()) {
        extendedState =
            state.addVariables(newVariable, ImmutableSet.of(), NewVariableValue.UNCONSTRAINED);
      } else {
        extendedState =
            state.addVariables(ImmutableSet.of(), newVariable, NewVariableValue.UNCONSTRAINED);
      }
    }

    Collection<PartialState> expressions =
        pIastExpressionAssignmentStatement
            .getRightHandSide()
            .accept(
                new NumericRightHandSideVisitor(
                    extendedState.getValue().getEnvironment(),
                    state.getManager(),
                    edge,
                    precision,
                    logger));

    ImmutableList.Builder<NumericState> successorsBuilder = new ImmutableList.Builder<>();

    for (PartialState partialState : expressions) {
      NumericState newState =
          extendedState.assignTreeExpression(variable.get(), partialState.getPartialConstraint());
      if (!newState.getValue().isBottom()) {
        successorsBuilder.add(newState);
      } else {
        // Dispose value that will not be used
        newState.getValue().dispose();
      }
    }
    extendedState.getValue().dispose();
    return successorsBuilder.build();
  }

  @Override
  public Collection<NumericState> visit(
      CFunctionCallAssignmentStatement pIastFunctionCallAssignmentStatement)
      throws UnrecognizedCodeException {
    if (pIastFunctionCallAssignmentStatement.getLeftHandSide() instanceof CIdExpression) {
      CIdExpression expression =
          (CIdExpression) pIastFunctionCallAssignmentStatement.getLeftHandSide();
      Optional<NumericVariable> variable =
          NumericVariable.valueOf(
              expression.getDeclaration(),
              edge.getSuccessor(),
              precision,
              state.getManager(),
              logger);

      if (variable.isPresent()
          && state.getValue().getEnvironment().containsVariable(variable.get())) {
        if (returnVariable.isPresent()) {
          return handleReturnVariable(variable.get());
        } else {
          // Function is extern, so the value can not be constrained
          Interval interval;
          final boolean returnTypeIsSigned;

          CFunctionDeclaration declaration =
              pIastFunctionCallAssignmentStatement.getRightHandSide().getDeclaration();
          CType type = (declaration == null) ? null : declaration.getType().getReturnType();
          if (type instanceof CSimpleType) {
            returnTypeIsSigned = ((CSimpleType) type).isSigned();
          } else {
            returnTypeIsSigned = true;
          }
          if (variable.get().getSimpleType().isSigned() || returnTypeIsSigned) {
            interval = PartialState.UNCONSTRAINED_INTERVAL;
          } else {
            interval = PartialState.UNSIGNED_UNCONSTRAINED_INTERVAL;
          }

          NumericState newState =
              state.assignTreeExpression(variable.get(), new ConstantTreeNode(interval));
          return ImmutableSet.of(newState);
        }
      }
    }

    // If it can not be handled do nothing
    return ImmutableSet.of(state.createCopy());
  }

  private Collection<NumericState> handleReturnVariable(Variable variable) {
    TreeNode right = new VariableTreeNode(returnVariable.get());
    NumericState newState = state.assignTreeExpression(variable, right);
    return ImmutableSet.of(newState);
  }

  @Override
  public Collection<NumericState> visit(CFunctionCallStatement pIastFunctionCallStatement)
      throws UnrecognizedCodeException {
    // Nothing to do for a function call
    return ImmutableSet.of(state);
  }
}