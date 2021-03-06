package com.munch.exchange.parts.composite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Slider;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import com.munch.exchange.job.objectivefunc.MacdObjFunc;
import com.munch.exchange.model.core.ExchangeRate;
import com.munch.exchange.model.core.historical.HistoricalPoint;
import com.munch.exchange.services.IExchangeRateProvider;
public class RateChartEMAComposite extends Composite {
	
	public static final String EMA="EMA";
	
	
	private static Logger logger = Logger.getLogger(RateChartEMAComposite.class);
	
	@Inject
	IEclipseContext context;
	
	@Inject
	private EModelService modelService;
	
	@Inject
	EPartService partService;
	
	@Inject
	private MApplication application;
	
	@Inject
	private Shell shell;
	
	@Inject
	private IEventBroker eventBroker;
	
	@Inject
	private ExchangeRate rate;
	
	@Inject
	private IExchangeRateProvider exchangeRateProvider;
	
	
	//Renderers
	private XYLineAndShapeRenderer mainPlotRenderer;
	private XYLineAndShapeRenderer secondPlotrenderer;
	
	//Series Collections
	private XYSeriesCollection mainCollection;
	private XYSeriesCollection secondCollection;
	
	// set the period and max profit
	private int[] period=new int[2];
	private float maxProfit=0;
	
	private Button emaBtn;
	private Label emaLblAlpha;
	private Slider emaSlider;
	private XYSeries emaSeries;
	
	@Inject
	public RateChartEMAComposite(Composite parent) {
		super(parent, SWT.NONE);
		
		this.setLayout(new GridLayout(4, false));
		
		emaBtn = new Button(this, SWT.CHECK);
		emaBtn.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				emaSlider.setEnabled(emaBtn.getSelection());
				//if(emaBtn.getSelection())
				resetChartDataSet();
				
				if(!emaBtn.getSelection())
					fireCollectionRemoved();
				
			}
		});
		emaBtn.setText("EMA");
		
		Label lblAlpha = new Label(this, SWT.NONE);
		lblAlpha.setText("Alpha:");
		
		emaLblAlpha = new Label(this, SWT.NONE);
		emaLblAlpha.setText("0.600");
		
		emaSlider = new Slider(this, SWT.NONE);
		emaSlider.setMaximum(1000);
		emaSlider.setMinimum(1);
		emaSlider.setSelection(600);
		emaSlider.setEnabled(false);
		emaSlider.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				String alphaStr = String.format("%.3f", ( (float) emaSlider.getSelection())/1000);
				emaLblAlpha.setText(alphaStr.replace(",", "."));
				if(emaSlider.isEnabled())
					resetChartDataSet();
				
			}
		});
		emaSlider.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
	}
	
	public Button getCheckButton(){
		return emaBtn;
	}
	
	public void setRenderers(XYLineAndShapeRenderer mainPlotRenderer,XYLineAndShapeRenderer secondPlotrenderer){
		this.mainPlotRenderer=mainPlotRenderer;
		this.secondPlotrenderer=secondPlotrenderer;
	}
	
	public void setSeriesCollections(XYSeriesCollection mainCollection,XYSeriesCollection secondCollection){
		this.mainCollection=mainCollection;
		this.secondCollection=secondCollection;
	}

	public void setPeriodandMaxProfit(int[] period,float maxProfit){
		this.period=period;
		this.maxProfit=maxProfit;
		
		resetChartDataSet();
	}
	
	private void  clearCollections(){
		int pos=mainCollection.indexOf(EMA);
		if(pos>=0){
			mainCollection.removeSeries(pos);
		}
		
		//mainPlotRenderer.addChangeListener(listener);
	}
	
	private void resetChartDataSet() {
		
		clearCollections();
		
		if(emaBtn.getSelection()){
			emaSeries=rate.getHistoricalData().getEMA(HistoricalPoint.FIELD_Close, Float.parseFloat(emaLblAlpha.getText()),"EMA");
			mainCollection.addSeries(MacdObjFunc.reduceSerieToPeriod(emaSeries,period));
			
			int pos=mainCollection.indexOf(EMA);
			if(pos>=0){
				mainPlotRenderer.setSeriesShapesVisible(pos, false);
				mainPlotRenderer.setSeriesLinesVisible(pos, true);
				mainPlotRenderer.setSeriesStroke(pos,new BasicStroke(2.0f));
				mainPlotRenderer.setSeriesPaint(pos, Color.DARK_GRAY);
			}
			
		}
	}
	
	// ///////////////////////////
	// // LISTERNER ////
	// ///////////////////////////
	private List<CollectionRemovedListener> listeners = new LinkedList<CollectionRemovedListener>();

	public void addCollectionRemovedListener(CollectionRemovedListener l) {
		listeners.add(l);
	}

	public void removeCollectionRemovedListener(CollectionRemovedListener l) {
		listeners.remove(l);
	}

	private void fireCollectionRemoved() {
		for (CollectionRemovedListener l : listeners)
			l.CollectionRemoved();
	}

}
