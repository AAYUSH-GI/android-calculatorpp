package org.solovyev.android.calculator;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static org.solovyev.android.calculator.CalculatorEventType.*;

/**
 * User: serso
 * Date: 9/20/12
 * Time: 8:24 PM
 */
public class CalculatorDisplayImpl implements CalculatorDisplay {

    @NotNull
    private CalculatorEventData lastCalculatorEventData;

    @Nullable
    private CalculatorDisplayView view;

    @NotNull
    private final Object viewLock = new Object();

    @NotNull
    private CalculatorDisplayViewState viewState = CalculatorDisplayViewStateImpl.newDefaultInstance();

    @NotNull
    private final Calculator calculator;

    public CalculatorDisplayImpl(@NotNull Calculator calculator) {
        this.calculator = calculator;
        this.lastCalculatorEventData = CalculatorEventDataImpl.newInstance(calculator.createFirstEventDataId());
        this.calculator.addCalculatorEventListener(this);
    }

    @Override
    public void setView(@Nullable CalculatorDisplayView view) {
        synchronized (viewLock) {
            this.view = view;

            if (view != null) {
                this.view.setState(viewState);
            }
        }
    }

    @Nullable
    @Override
    public CalculatorDisplayView getView() {
        return this.view;
    }

    @NotNull
    @Override
    public CalculatorDisplayViewState getViewState() {
        return this.viewState;
    }

    @Override
    public void setViewState(@NotNull CalculatorDisplayViewState newViewState) {
        synchronized (viewLock) {
            final CalculatorDisplayViewState oldViewState = setViewState0(newViewState);

            this.calculator.fireCalculatorEvent(display_state_changed, new CalculatorDisplayChangeEventDataImpl(oldViewState, newViewState));
        }
    }

    private void setViewStateForSequence(@NotNull CalculatorDisplayViewState newViewState, @NotNull Long sequenceId) {
        synchronized (viewLock) {
            final CalculatorDisplayViewState oldViewState = setViewState0(newViewState);

            this.calculator.fireCalculatorEvent(display_state_changed, new CalculatorDisplayChangeEventDataImpl(oldViewState, newViewState), sequenceId);
        }
    }

    // must be synchronized with viewLock
    @NotNull
    private CalculatorDisplayViewState setViewState0(@NotNull CalculatorDisplayViewState newViewState) {
        final CalculatorDisplayViewState oldViewState = this.viewState;

        this.viewState = newViewState;
        if (this.view != null) {
            this.view.setState(newViewState);
        }
        return oldViewState;
    }

    @Override
    @NotNull
    public CalculatorEventData getLastEventData() {
        return lastCalculatorEventData;
    }

    @Override
    public void onCalculatorEvent(@NotNull CalculatorEventData calculatorEventData,
                                  @NotNull CalculatorEventType calculatorEventType,
                                  @Nullable Object data) {
        if (calculatorEventType.isOfType(calculation_result, calculation_failed, calculation_cancelled)) {

            if (calculatorEventData.isAfter(lastCalculatorEventData)) {
                lastCalculatorEventData = calculatorEventData;
            }

            switch (calculatorEventType) {
                case calculation_result:
                    processCalculationResult((CalculatorEvaluationEventData)calculatorEventData, (CalculatorOutput) data);
                    break;
                case calculation_cancelled:
                    processCalculationCancelled((CalculatorEvaluationEventData)calculatorEventData);
                    break;
                case calculation_failed:
                    processCalculationFailed((CalculatorEvaluationEventData)calculatorEventData, (CalculatorFailure) data);
                    break;
            }

        }
    }

    private void processCalculationFailed(@NotNull CalculatorEvaluationEventData calculatorEventData, @NotNull CalculatorFailure data) {

        final CalculatorEvalException calculatorEvalException = data.getCalculationEvalException();

        final String errorMessage;
        if (calculatorEvalException != null) {
            errorMessage = CalculatorMessages.getBundle().getString(CalculatorMessages.syntax_error);
        } else {
            final CalculatorParseException calculationParseException = data.getCalculationParseException();
            if (calculationParseException != null) {
                errorMessage = calculationParseException.getLocalizedMessage();
            } else {
                errorMessage = CalculatorMessages.getBundle().getString(CalculatorMessages.syntax_error);
            }
        }

        this.setViewStateForSequence(CalculatorDisplayViewStateImpl.newErrorState(calculatorEventData.getOperation(), errorMessage), calculatorEventData.getSequenceId());
    }

    private void processCalculationCancelled(@NotNull CalculatorEvaluationEventData calculatorEventData) {
        final String errorMessage = CalculatorMessages.getBundle().getString(CalculatorMessages.syntax_error);

        this.setViewState(CalculatorDisplayViewStateImpl.newErrorState(calculatorEventData.getOperation(), errorMessage));
    }

    private void processCalculationResult(@NotNull CalculatorEvaluationEventData calculatorEventData, @NotNull CalculatorOutput data) {
        final String stringResult = data.getStringResult();
        this.setViewState(CalculatorDisplayViewStateImpl.newValidState(calculatorEventData.getOperation(), data.getResult(), stringResult, 0));
    }
}
