package com.munch.exchange.wizard;

import org.eclipse.jface.wizard.Wizard;

public class ArchitectureOptimizationWizard extends Wizard {

	public ArchitectureOptimizationWizard() {
		setWindowTitle("New Wizard");
	}

	@Override
	public void addPages() {
	}

	@Override
	public boolean performFinish() {
		return false;
	}

}
