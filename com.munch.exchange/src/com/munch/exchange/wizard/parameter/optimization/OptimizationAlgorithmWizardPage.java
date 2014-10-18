package com.munch.exchange.wizard.parameter.optimization;

import org.apache.log4j.Logger;
import org.eclipse.jface.wizard.WizardPage;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Spinner;

import com.munch.exchange.model.core.optimization.AlgorithmParameters;

import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

public class OptimizationAlgorithmWizardPage extends WizardPage {
	
	private static Logger logger = Logger.getLogger(OptimizationAlgorithmWizardPage.class);
	
	
	private AlgorithmParameters<Double> optLearnParam;
	
	
	private Combo comboAlgorithmType;
	private Spinner spinnerNumberOfSteps;
	

	/**
	 * Create the wizard.
	 */
	public OptimizationAlgorithmWizardPage(AlgorithmParameters<Double> optLearnParam) {
		super("wizardPage");
		setTitle("Algorithm Selection");
		setDescription("Please select the optimization algorithm");
		
		this.optLearnParam=optLearnParam;
	}

	/**
	 * Create contents of the wizard.
	 * @param parent
	 */
	public void createControl(Composite parent) {
		Composite container = new Composite(parent, SWT.NULL);

		setControl(container);
		container.setLayout(new GridLayout(2, false));
		
		Label lblAlgorithmType = new Label(container, SWT.NONE);
		lblAlgorithmType.setText("Algorithm type:");
		
		comboAlgorithmType = new Combo(container, SWT.NONE);
		comboAlgorithmType.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				saveParameters();
			}
		});
		comboAlgorithmType.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		comboAlgorithmType.add(AlgorithmParameters.ALGORITHM_Evolution_Strategy);
		comboAlgorithmType.setText(AlgorithmParameters.ALGORITHM_Evolution_Strategy);
		if(!optLearnParam.getType().isEmpty()){
			comboAlgorithmType.setText(optLearnParam.getType());
		}
		
		Label lblTerminationCriterion = new Label(container, SWT.NONE);
		lblTerminationCriterion.setText("Termination Criterion:");
		new Label(container, SWT.NONE);
		
		Label lblNumberOfSteps = new Label(container, SWT.NONE);
		lblNumberOfSteps.setText("Number of Steps:");
		
		spinnerNumberOfSteps = new Spinner(container, SWT.BORDER);
		spinnerNumberOfSteps.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				saveParameters();
			}
		});
		spinnerNumberOfSteps.setIncrement(1);
		spinnerNumberOfSteps.setMaximum(200);
		spinnerNumberOfSteps.setMinimum(1);
		spinnerNumberOfSteps.setSelection(5);
		if(optLearnParam.hasParamKey(AlgorithmParameters.TERMINATION_Steps)){
			logger.info(optLearnParam);
			logger.info("Number of steps: "+optLearnParam.getIntegerParam(AlgorithmParameters.TERMINATION_Steps));
			spinnerNumberOfSteps.setSelection(optLearnParam.getIntegerParam(AlgorithmParameters.TERMINATION_Steps));
		}
		
		saveParameters();
	}
	
	
	
	private void saveParameters(){
		optLearnParam.setType(comboAlgorithmType.getText());
		optLearnParam.setParam(AlgorithmParameters.TERMINATION_Steps, spinnerNumberOfSteps.getSelection());
		
	}
	

}