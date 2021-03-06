package com.munch.exchange.parts.neuralnetwork;

import java.util.Arrays;
import java.util.LinkedList;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.EclipseContextFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.e4.ui.workbench.modeling.EModelService;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.jface.wizard.WizardDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.wb.swt.ResourceManager;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.events.LearningEvent;
import org.neuroph.core.events.LearningEventListener;
import org.neuroph.core.learning.LearningRule;
import org.neuroph.nnet.learning.BackPropagation;
import org.neuroph.nnet.learning.ResilientPropagation;

import com.munch.exchange.IEventConstant;
import com.munch.exchange.dialog.AddTimeSeriesDialog;
import com.munch.exchange.job.NeuralNetworkDataLoader;
import com.munch.exchange.job.NeuralNetworkOptimizer;
import com.munch.exchange.job.Optimizer;
import com.munch.exchange.job.objectivefunc.NeuralNetworkOutputObjFunc;
import com.munch.exchange.model.core.DatePoint;
import com.munch.exchange.model.core.ExchangeRate;
import com.munch.exchange.model.core.Stock;
import com.munch.exchange.model.core.historical.HistoricalPoint;
import com.munch.exchange.model.core.neuralnetwork.Configuration;
import com.munch.exchange.model.core.neuralnetwork.NetworkArchitecture;
import com.munch.exchange.model.core.neuralnetwork.TimeSeries;
import com.munch.exchange.model.core.optimization.AlgorithmParameters;
import com.munch.exchange.parts.composite.RateChart;
import com.munch.exchange.parts.neuralnetwork.NeuralNetworkContentProvider.NeuralNetworkSerieCategory;
import com.munch.exchange.services.IExchangeRateProvider;
import com.munch.exchange.services.INeuralNetworkProvider;
import com.munch.exchange.wizard.parameter.architecture.ArchitectureOptimizationWizard;
import com.munch.exchange.wizard.parameter.learning.LearnParameterWizard;
import com.munch.exchange.wizard.parameter.optimization.OptimizationDoubleParamWizard;

import org.eclipse.swt.widgets.Group;

public class NeuralNetworkComposite extends Composite implements LearningEventListener{
	
	private static Logger logger = Logger.getLogger(NeuralNetworkComposite.class);
	
	private Stock stock;
	
	private boolean isLoaded=false;
	
	private INeuralNetworkProvider neuralNetworkProvider;
	
	private NeuralNetworkContentProvider contentProvider;
	
	private NeuralNetworkOutputObjFunc objFunc;
	
	private NeuralNetworkChart neuralNetworkChart;
	
	private double maxProfit=0;
	private double maxPenaltyProfit=0;
	
	@Inject
	IEclipseContext context;
	
	@Inject
	private EModelService modelService;
	
	@Inject
	EPartService partService;
	
	@Inject
	private MApplication application;
	
	@Inject
	private IExchangeRateProvider exchangeRateProvider;
	
	@Inject
	private Shell shell;
	
	@Inject
	private IEventBroker eventBroker;
	
	
	private NeuralNetworkOptimizer optimizer;
	
	private Text textMaxProfit;
	private Text textPenaltyProfit;
	private Text textError;
	private Text textGenError;
	private Tree tree;
	private TreeViewer treeViewer;
	private Combo comboConfig;
	private NeuralNetworkDataLoader nnd_loader;
	private Button btnSaveConfig;
	private Button btnDeleteConfig;
	private Combo comboPeriod;
	private Button btnActivateDayOf;
	private Menu menu;
	private MenuItem mntmAddSerie;
	private MenuItem mntmRemove;
	private Button btnStartTrain;
	
	
	@Inject
	public NeuralNetworkComposite(Composite parent,
			ExchangeRate rate,IEclipseContext ctxt,
			INeuralNetworkProvider nnProvider) {
		super(parent, SWT.NONE);
		this.stock=(Stock) rate;
		this.neuralNetworkProvider=nnProvider;
		contentProvider=new NeuralNetworkContentProvider(this.stock);
		
		
		setLayout(new GridLayout(1, false));
		
		SashForm sashForm = new SashForm(this, SWT.NONE);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		Composite compositeLeft = new Composite(sashForm, SWT.NONE);
		compositeLeft.setLayout(new GridLayout(1, false));
		
		Composite compositeLeftHeader = new Composite(compositeLeft, SWT.NONE);
		compositeLeftHeader.setLayout(new GridLayout(3, false));
		compositeLeftHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		Label lblConfig = new Label(compositeLeftHeader, SWT.NONE);
		lblConfig.setText("Config:");
		
		comboConfig = new Combo(compositeLeftHeader, SWT.NONE);
		comboConfig.setEnabled(false);
		comboConfig.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		Composite composite_2 = new Composite(compositeLeftHeader, SWT.NONE);
		GridLayout gl_composite_2 = new GridLayout(2, false);
		gl_composite_2.marginHeight = 0;
		composite_2.setLayout(gl_composite_2);
		composite_2.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, false, 1, 1));
		
		btnSaveConfig = new Button(composite_2, SWT.NONE);
		btnSaveConfig.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				if(isLoaded){
					neuralNetworkProvider.save(stock);
				}
				else{
					isLoaded=neuralNetworkProvider.load(stock);
					refreshGui();
				}
				
			}
		});
		btnSaveConfig.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
		btnSaveConfig.setText("Load");
		
		btnDeleteConfig = new Button(composite_2, SWT.NONE);
		btnDeleteConfig.setEnabled(false);
		btnDeleteConfig.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
			}
		});
		btnDeleteConfig.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 1, 1));
		btnDeleteConfig.setText("Delete");
		
		Label lblPeriod = new Label(compositeLeftHeader, SWT.NONE);
		lblPeriod.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblPeriod.setText("Period:");
		
		comboPeriod = new Combo(compositeLeftHeader, SWT.NONE);
		comboPeriod.setEnabled(false);
		comboPeriod.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
			}
		});
		comboPeriod.setItems(new String[] {"DAY", "HOUR", "MINUTE", "SECONDE"});
		comboPeriod.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		comboPeriod.setText("DAY");
		
		btnActivateDayOf = new Button(compositeLeftHeader, SWT.CHECK);
		btnActivateDayOf.setEnabled(false);
		btnActivateDayOf.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				stock.getNeuralNetwork().getConfiguration()
						.setDayOfWeekActivated(btnActivateDayOf.getSelection());
			}
		});
		btnActivateDayOf.setText("Day of week");
		
		Composite composite = new Composite(compositeLeft, SWT.NONE);
		composite.setLayout(new GridLayout(1, false));
		composite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		treeViewer = new TreeViewer(composite, SWT.BORDER| SWT.MULTI
				| SWT.V_SCROLL);
		treeViewer.setContentProvider(contentProvider);
		treeViewer.setInput(contentProvider.getRoot());
		
		tree = treeViewer.getTree();
		tree.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDown(MouseEvent e) {
				if(e.button==3 && tree.getSelection().length==1){
					TreeItem item=tree.getSelection()[0];
					if(item.getData() instanceof NeuralNetworkSerieCategory){
						mntmAddSerie.setEnabled(true);
						mntmRemove.setEnabled(false);
					}
					else if(item.getData() instanceof TimeSeries){
						mntmAddSerie.setEnabled(false);
						mntmRemove.setEnabled(true);
					}
					else{
						mntmAddSerie.setEnabled(false);
						mntmRemove.setEnabled(false);
					}
					menu.setVisible(true);
				}
				
			}
		});
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		tree.setHeaderVisible(true);
		tree.setLinesVisible(true);
		
		
		
		TreeViewerColumn treeViewerColumnInputSeries = new TreeViewerColumn(treeViewer, SWT.NONE);
		treeViewerColumnInputSeries.setLabelProvider(new InputSeriesLabelProvider());
		TreeColumn trclmnInputSeries = treeViewerColumnInputSeries.getColumn();
		trclmnInputSeries.setWidth(150);
		trclmnInputSeries.setText("Input Series");
		
		TreeViewerColumn treeViewerColumnNbOfValues = new TreeViewerColumn(treeViewer, SWT.NONE);
		treeViewerColumnNbOfValues.setLabelProvider(new NbOfValuesLabelProvider());
		TreeColumn trclmnNbOfValues = treeViewerColumnNbOfValues.getColumn();
		trclmnNbOfValues.setWidth(100);
		trclmnNbOfValues.setText("Nb. of values");
		
		TreeViewerColumn treeViewerColumnTimeLeft = new TreeViewerColumn(treeViewer, SWT.NONE);
		treeViewerColumnTimeLeft.setLabelProvider(new TimeLeftLabelProvider());
		TreeColumn trclmnTimeLeft = treeViewerColumnTimeLeft.getColumn();
		trclmnTimeLeft.setWidth(100);
		trclmnTimeLeft.setText("Time left");
		
		menu = new Menu(tree);
		tree.setMenu(menu);
		
		mntmAddSerie = new MenuItem(menu, SWT.NONE);
		mntmAddSerie.setImage(ResourceManager.getPluginImage("com.munch.exchange", "icons/add.png"));
		mntmAddSerie.setEnabled(false);
		mntmAddSerie.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				//logger.info("Add serie selected");
				TreeItem item=tree.getSelection()[0];
				NeuralNetworkSerieCategory category=(NeuralNetworkSerieCategory) item.getData();
				
				AddTimeSeriesDialog dialog=new AddTimeSeriesDialog(shell, category.name, stock.getNeuralNetwork().getConfiguration());
				if(dialog.open()==AddTimeSeriesDialog.OK){
					//TODO
					refreshTimeSeries();
					fireReadyToTrain();
				}
			}
		});
		mntmAddSerie.setText("Add Serie");
		
		mntmRemove = new MenuItem(menu, SWT.NONE);
		mntmRemove.setImage(ResourceManager.getPluginImage("com.munch.exchange", "icons/delete.png"));
		mntmRemove.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				TreeItem item=tree.getSelection()[0];
				TimeSeries series=(TimeSeries) item.getData();
				
				stock.getNeuralNetwork().getConfiguration().getAllTimeSeries().remove(series);
				tree.removeAll();
				
				refreshTimeSeries();
				fireReadyToTrain();
				
			}
		});
		mntmRemove.setEnabled(false);
		mntmRemove.setText("Remove");
		
		Composite compositeLeftBottom = new Composite(compositeLeft, SWT.NONE);
		compositeLeftBottom.setLayout(new GridLayout(2, false));
		compositeLeftBottom.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		//btnOpt.setEnabled(false);
		
		Label lblMaxProfit = new Label(compositeLeftBottom, SWT.NONE);
		lblMaxProfit.setText("Max Profit:");
		
		textMaxProfit = new Text(compositeLeftBottom, SWT.BORDER);
		textMaxProfit.setEnabled(false);
		textMaxProfit.setEditable(false);
		textMaxProfit.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		
		Label lblPenalityProfit = new Label(compositeLeftBottom, SWT.NONE);
		lblPenalityProfit.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblPenalityProfit.setText("Penality Profit:");
		
		textPenaltyProfit = new Text(compositeLeftBottom, SWT.BORDER);
		textPenaltyProfit.setEditable(false);
		textPenaltyProfit.setEnabled(false);
		textPenaltyProfit.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		Composite compositeRight = new Composite(sashForm, SWT.NONE);
		compositeRight.setLayout(new GridLayout(1, false));
		
		Group grpLearning = new Group(compositeRight, SWT.NONE);
		grpLearning.setLayout(new GridLayout(6, false));
		grpLearning.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		grpLearning.setText("Learning");
		
		Label lblConfiguration = new Label(grpLearning, SWT.NONE);
		lblConfiguration.setText("Configuration:");
		
		Button btnArchOptConf = new Button(grpLearning, SWT.NONE);
		btnArchOptConf.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				
				//TODO Delete
				neuralNetworkProvider.createAllInputPoints(stock);
				
				Configuration conf=stock.getNeuralNetwork().getConfiguration();
				
				ArchitectureOptimizationWizard wizard=new ArchitectureOptimizationWizard(
						conf.getOptArchitectureParam().createCopy(),conf.getNumberOfInputNeurons());
				WizardDialog dialog = new WizardDialog(shell, wizard);
				if (dialog.open() == Window.OK){
					stock.getNeuralNetwork().getConfiguration().setOptArchitectureParam(
							wizard.getOptArchitectureParam());
				}
			}
		});
		btnArchOptConf.setText("Arch. Opt.");
		
		Button btnLearnOptConf = new Button(grpLearning, SWT.NONE);
		btnLearnOptConf.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				//logger.info(stock.getNeuralNetwork().getConfiguration().getOptLearnParam());
				//logger.info(stock.getNeuralNetwork().getConfiguration().getOptLearnParam().createCopy());
				OptimizationDoubleParamWizard wizard=new OptimizationDoubleParamWizard(
						stock.getNeuralNetwork().getConfiguration().getOptLearnParam().createCopy());
				WizardDialog dialog = new WizardDialog(shell, wizard);
				if (dialog.open() == Window.OK){
					stock.getNeuralNetwork().getConfiguration().setOptLearnParam(wizard.getOptLearnParam());
				}
				
			}
		});
		btnLearnOptConf.setText("Learn Opt.");
		
		Button btnLearnAlg = new Button(grpLearning, SWT.NONE);
		btnLearnAlg.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				//logger.info(stock.getNeuralNetwork().getConfiguration().getLearnParam());
				//logger.info(stock.getNeuralNetwork().getConfiguration().getLearnParam().createCopy());
				LearnParameterWizard wizard=new LearnParameterWizard(
						stock.getNeuralNetwork().getConfiguration().getLearnParam().createCopy());
				WizardDialog dialog = new WizardDialog(shell, wizard);
				if (dialog.open() == Window.OK){
					stock.getNeuralNetwork().getConfiguration().setLearnParam(wizard.getParam());
					//logger.info("New Param: "+wizard.getParam());
					//logger.info("New Param: "+stock.getNeuralNetwork().getConfiguration().getLearnParam());
				}
				
			}
		});
		btnLearnAlg.setText("Learn Alg.");
		
		Label label = new Label(grpLearning, SWT.NONE);
		label.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		btnStartTrain = new Button(grpLearning, SWT.NONE);
		btnStartTrain.setEnabled(false);
		btnStartTrain.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				
				logger.info("Start Train click!");
				
				neuralNetworkProvider.createAllInputPoints(stock);
				DataSet trainingSet=stock.getNeuralNetwork().getConfiguration().getTrainingDataSet();
				
				
				int dimension=5;
				
				AlgorithmParameters<boolean[]> optArchitectureParam=stock.getNeuralNetwork().getConfiguration().getOptArchitectureParam();
				
				if(optArchitectureParam.hasParamKey(AlgorithmParameters.MinDimension)){
					dimension=optArchitectureParam.getIntegerParam(AlgorithmParameters.MinDimension);
				}
				
				NeuralNetworkOptimizer
				optimizer=new NeuralNetworkOptimizer(stock, stock.getNeuralNetwork().getConfiguration(),
						trainingSet, eventBroker, dimension);
				
				optimizer.schedule();
				
				
				/*
				int nbOfInput=trainingSet.getRowAt(0).getInput().length;
				logger.info("Number of input: "+nbOfInput);
				
				int nbofInner=4;
				double[] con=new double[NetworkArchitecture.calculateActivatedConnectionsSize(nbOfInput, nbofInner)];
				for(int i=0;i<con.length;i++){
					con[i]=1;
				}
				logger.info("Number of doubles: "+con.length);
				
				NetworkArchitecture arch=new NetworkArchitecture(nbOfInput,nbofInner,con);
				
				*/
				/*
				// create multi layer perceptron
				List<Integer> neuronsInLayers=new LinkedList<Integer>();
				neuronsInLayers.add(nbOfInput);
				neuronsInLayers.add(nbOfInput/2);
				neuronsInLayers.add(nbOfInput/4);
				neuronsInLayers.add(1);
				
				NeuronProperties neuronProperties = new NeuronProperties();
                neuronProperties.setProperty("useBias", false);
                neuronProperties.setProperty("transferFunction", TransferFunctionType.SIGMOID);
				
		        MultiLayerPerceptron myMlPerceptron = new MultiLayerPerceptron(neuronsInLayers, neuronProperties);
		        
		        
		        */
				/*
				org.neuroph.core.NeuralNetwork myMlPerceptron=arch.getNetworks().getFirst();
		        stock.getNeuralNetwork().getConfiguration().setCurrentNetwork(arch.getNetworks().getFirst());
		        
		        ResilientPropagation resilientPropagation=new ResilientPropagation();
		        resilientPropagation.setMaxIterations(3);
		        
		        myMlPerceptron.setLearningRule(resilientPropagation);
		        
		        LearningRule learningRule = myMlPerceptron.getLearningRule();
		        learningRule.addListener(NeuralNetworkComposite.this);
		        
		        
		        // learn the training set
		        System.out.println("Training neural network...");
		        myMlPerceptron.learn(trainingSet);

		        // test perceptron
		        System.out.println("Testing trained neural network");
		        testNeuralNetwork(myMlPerceptron, trainingSet);
				*/
		       
				
			}
		});
		btnStartTrain.setText("Start");
		
		Composite compositeRightHeader = new Composite(compositeRight, SWT.NONE);
		compositeRightHeader.setLayout(new GridLayout(4, false));
		compositeRightHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		Label lblError = new Label(compositeRightHeader, SWT.NONE);
		lblError.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblError.setText("Error:");
		
		textError = new Text(compositeRightHeader, SWT.BORDER);
		textError.setEnabled(false);
		textError.setEditable(false);
		textError.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		Label lblGenError = new Label(compositeRightHeader, SWT.NONE);
		lblGenError.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 1, 1));
		lblGenError.setText("Gen. Error:");
		
		textGenError = new Text(compositeRightHeader, SWT.BORDER);
		textGenError.setEnabled(false);
		textGenError.setEditable(false);
		textGenError.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		Composite compositeGraph = new Composite(compositeRight, SWT.NONE);
		compositeGraph.setLayout(new GridLayout(1, false));
		compositeGraph.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		sashForm.setWeights(new int[] {401, 595});
		
		//TODO
		createNeuralNetworkChart(ctxt,compositeGraph);
		
		
		treeViewer.refresh();
		
		loadNeuralData(ctxt);
		
		fireReadyToTrain();
		
	}
	
	private void createNeuralNetworkChart(IEclipseContext context, Composite parentComposite){
		//Create a context instance
		IEclipseContext localContact=EclipseContextFactory.create();
		localContact.set(Composite.class, parentComposite);
		localContact.setParent(context);
						
		//////////////////////////////////
		//Create the Chart Composite
		//////////////////////////////////
		neuralNetworkChart=ContextInjectionFactory.make( NeuralNetworkChart.class,localContact);
		neuralNetworkChart.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
	}
	
	
	private void fireReadyToTrain(){
		Configuration config=stock.getNeuralNetwork().getConfiguration();
		boolean readyToTrain=config!=null && config.getAllTimeSeries()!=null && config.getOutputPointList()!=null &&
				config.getAllTimeSeries().size()>0 && config.getOutputPointList().size()>0;
		btnStartTrain.setEnabled(readyToTrain);
		
	}
	
	
	private void loadNeuralData(IEclipseContext context){
		
		IEclipseContext localContact=EclipseContextFactory.create();
		localContact.setParent(context);
		
		if(nnd_loader==null){
			nnd_loader=ContextInjectionFactory.make( NeuralNetworkDataLoader.class,localContact);
		}
		nnd_loader.schedule();
	}
	
	private void changeLoadedState(){
		if(!isLoaded)return;
		
		btnSaveConfig.setText("Save");
		btnDeleteConfig.setEnabled(true);
		btnActivateDayOf.setEnabled(true);
		comboConfig.setEnabled(true);
		comboPeriod.setEnabled(true);
		
		
	}
	
	private void refreshTimeSeries(){
		this.contentProvider.refreshCategories();
		
		treeViewer.refresh();
	}
	
	private void refreshGui(){
		
		refreshComboConfig();
		changeLoadedState();
		
		treeViewer.refresh();
	}
	
	private void refreshComboConfig(){
		LinkedList<Configuration> configList=stock.getNeuralNetwork().getConfigurations();
		
		
		//logger.info("---->  1 Number of configs: "+configList.size());
		
		if(configList.size()==0){
			Configuration conf=new Configuration();
			conf.setName("New Config");
			configList.add(conf);
			stock.getNeuralNetwork().setCurrentConfiguration(conf.getName());
		}
		
		//logger.info("---->  1 Number of configs: "+configList.size());
		
		for(Configuration conf:configList){
			//logger.info("---->  Config name: "+conf.getName());
			comboConfig.add(conf.getName());
		}
		
		//logger.info("---->  Current Config name: "+stock.getNeuralNetwork().getCurrentConfiguration());
		comboConfig.setText(stock.getNeuralNetwork().getCurrentConfiguration());
	}
	
	
	private NeuralNetworkOutputObjFunc getObjFunc(){
		if(objFunc!=null)return objFunc;
		
		objFunc=new NeuralNetworkOutputObjFunc(
				 HistoricalPoint.FIELD_Close,
				 RateChart.PENALTY,
				 stock.getHistoricalData().getNoneEmptyPoints(),
				 maxProfit
				 );
		return objFunc;
	}
	
	
	//################################
	//##       Event Reaction       ##
	//################################
	
	private boolean isCompositeAbleToReact(String rate_uuid){
		if (this.isDisposed())
			return false;
		if (rate_uuid == null || rate_uuid.isEmpty())
			return false;

		ExchangeRate incoming = exchangeRateProvider.load(rate_uuid);
		if (incoming == null || stock == null )
			return false;
		if (!incoming.getUUID().equals(stock.getUUID()))
			return false;
		
		return true;
	}
	
	@Inject
	private void neuralNetworkDataLoaded(
			@Optional @UIEventTopic(IEventConstant.NEURAL_NETWORK_DATA_LOADED) String rate_uuid) {

		if (!isCompositeAbleToReact(rate_uuid))
			return;
		
		//logger.info("---->  Message recieved!!: ");
		isLoaded=true;
		
		refreshGui();
		refreshTimeSeries();
		
		fireReadyToTrain();
	}
	
	@Inject
	private void historicalDataLoaded(
			@Optional @UIEventTopic(IEventConstant.HISTORICAL_DATA_LOADED) String rate_uuid) {

		if (!isCompositeAbleToReact(rate_uuid))
			return;

		maxProfit=this.stock.getHistoricalData().calculateMaxProfit(DatePoint.FIELD_Close);
		
		if(stock.getNeuralNetwork().getConfiguration()==null){
			return;
		}
		
		stock.getNeuralNetwork().getConfiguration().setOutputPointList(neuralNetworkProvider.calculateMaxProfitOutputList(stock,RateChart.PENALTY));
		maxPenaltyProfit=maxProfit-getObjFunc().compute(stock.getNeuralNetwork().getConfiguration().getOutputPointList().toDoubleArray(), null);	
	
		String maxPanaltyProfitStr = String.format("%,.2f%%",maxPenaltyProfit * 100);
		String maxProfitStr = String.format("%,.2f%%",maxProfit * 100);
		
		textMaxProfit.setText(maxProfitStr);
		textPenaltyProfit.setText(maxPanaltyProfitStr);
		
		
		fireReadyToTrain();
		
	}
	
	@Override
    public void handleLearningEvent(LearningEvent event) {
        BackPropagation bp = (BackPropagation)event.getSource();
        System.out.println(bp.getCurrentIteration() + ". iteration : "+ bp.getTotalNetworkError());
        
        eventBroker.send(IEventConstant.NEURAL_NETWORK_NEW_CURRENT,stock.getUUID());
        
        
    } 
	
	/**
     * Prints network output for the each element from the specified training set.
     * @param neuralNet neural network
     * @param trainingSet training set
     */
    public static void testNeuralNetwork(NeuralNetwork neuralNet, DataSet testSet) {
    	
    	String[] nn_output=new String[testSet.getRows().size()];
    	String[] t_output=new String[testSet.getRows().size()];
    	
    	int pos=0;
        for(DataSetRow testSetRow : testSet.getRows()) {
            neuralNet.setInput(testSetRow.getInput());
            neuralNet.calculate();
            double[] networkOutput = neuralNet.getOutput();
            //String.format("%,.2f%%",maxPenaltyProfit * 100);
            nn_output[pos]=String.format("%,.2f%%",networkOutput[0]);
            t_output[pos]=String.format("%,.2f%%",testSetRow.getDesiredOutput()[0]);
            
            pos++;
            
            
            //System.out.print("Input: " + Arrays.toString( testSetRow.getInput() ) );
            //System.out.println(" Output: " + Arrays.toString( networkOutput) );
        }
        
        
        System.out.println("Output NN: " + Arrays.toString( nn_output ) );
        System.out.println("Output   : " + Arrays.toString( t_output) );
        
    }
	
	//################################
	//##     ColumnLabelProvider    ##
	//################################
	
	class InputSeriesLabelProvider extends ColumnLabelProvider{

		@Override
		public String getText(Object element) {
			if(element instanceof NeuralNetworkSerieCategory){
				NeuralNetworkSerieCategory el=(NeuralNetworkSerieCategory) element;
				return el.name.getCategoryLabel();
			}
			else if(element instanceof TimeSeries){
				TimeSeries el=(TimeSeries) element;
				return String.valueOf(el.getName());
			}
			return super.getText(element);
		}
		
	}
	
	class NbOfValuesLabelProvider extends ColumnLabelProvider{

		@Override
		public String getText(Object element) {
			if(element instanceof NeuralNetworkSerieCategory){
				//NeuralNetworkSerieCategory el=(NeuralNetworkSerieCategory) element;
				return "-";
			}
			else if(element instanceof TimeSeries){
				TimeSeries el=(TimeSeries) element;
				return String.valueOf(el.getNumberOfPastValues());
			}
			return super.getText(element);
		}
		
	}
	
	class TimeLeftLabelProvider extends ColumnLabelProvider{

		@Override
		public String getText(Object element) {
			if(element instanceof NeuralNetworkSerieCategory){
				//NeuralNetworkSerieCategory el=(NeuralNetworkSerieCategory) element;
				return "-";
			}
			else if(element instanceof TimeSeries){
				TimeSeries el=(TimeSeries) element;
				return String.valueOf(el.isTimeRemainingActivated());
			}
			return super.getText(element);
		}
		
	}
}
