package com.munch.exchange.model.core.neuralnetwork;

import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedList;

import org.apache.log4j.Logger;
import org.neuroph.core.NeuralNetwork;
import org.neuroph.core.data.DataSet;
import org.neuroph.core.data.DataSetRow;
import org.neuroph.core.learning.LearningRule;
import org.neuroph.nnet.MultiLayerPerceptron;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.munch.exchange.model.core.optimization.AlgorithmParameters;
import com.munch.exchange.model.core.optimization.OptimizationResults;
import com.munch.exchange.model.core.optimization.OptimizationResults.Type;
import com.munch.exchange.model.tool.DateTool;
import com.munch.exchange.model.xml.XmlParameterElement;

public class Configuration extends XmlParameterElement {
	
	private static Logger logger = Logger.getLogger(Configuration.class);
	
	static final String FIELD_Period="Period";
	static final String FIELD_DayOfWeekActivated="DayOfWeekActivated";
	static final String FIELD_DayOfWeek="Day of Week";
	static final String FIELD_Name="Name";
	static final String FIELD_LastUpdate="LastUpdate";
	static final String FIELD_AllTimeSeries="AllTimeSeries";
	static final String FIELD_OutputPointList="OutputPointList";
	static final String FIELD_LastInputPointDate="LastInputPointDate";
	static final String FIELD_OptLearnParam="OptLearnParam";
	
	private PeriodType period=PeriodType.DAY;
	private boolean dayOfWeekActivated=false;
	private String Name="New Neural Network Configuration";
	private Calendar lastUpdate=Calendar.getInstance();
	
	private LinkedList<TimeSeries> allTimeSeries=new LinkedList<TimeSeries>();
	private ValuePointList outputPointList=new ValuePointList();
	
	private Calendar lastInputPointDate=null;
		
	private AlgorithmParameters<double[]> optLearnParam=new AlgorithmParameters<double[]>("Optimization_learn_parameters");
	private AlgorithmParameters<boolean[]> optArchitectureParam=new AlgorithmParameters<boolean[]>("Optimization_architecture_parameters");
	private LearnParameters learnParam=new LearnParameters("Neural_Network_learn_parameters");
	
	private int maxNumberOfSavedAchitectures=200;
	private LinkedList<NetworkArchitecture> networkArchitectures=new LinkedList<NetworkArchitecture>();
	private HashMap<Integer, OptimizationResults> netArchiOptResultMap=new HashMap<Integer, OptimizationResults>();
	
	private int numberOfInputNeurons;
	private DataSet trainingSet=null;
	
	
	
	public synchronized NetworkArchitecture searchArchitecture(boolean[] actConsArray){
		return searchArchitecture(actConsArray,false);
	}
	
	/**
	 * Knowing the connections array this method will search the corresponding architecture
	 * if no architecture was found a new one will be build
	 * 
	 * @param actConsArray
	 * @return
	 */
	public synchronized NetworkArchitecture searchArchitecture(boolean[] actConsArray,boolean loggerOn){
		
		
		//logger.info("Archi"+Array.actConsArray);
		
		NetworkArchitecture searched=null;
		
		if(loggerOn)
			logger.info("Number of archi: "+networkArchitectures.size());
		
		
		//Search the architecture in the already created ones
		for(NetworkArchitecture architecture :networkArchitectures){
			if(architecture.getActConsArray().length==actConsArray.length){
				boolean isEqual=true;
				for(int i=0;i<actConsArray.length;i++){
					if(architecture.getActConsArray()[i]!=actConsArray[i]){
						isEqual=false;break;
					}
				}
				if(isEqual){
					//logger.info("Architecture found!");
					if(loggerOn)
						logger.info("Architecture found!");
					searched=architecture;
					return searched;
				}
				
			}
		}
		
		//Create the architecture
		if(searched==null){
			int numberOfInnerNeurons=NetworkArchitecture.calculateNbOfInnerNeurons(
					actConsArray.length, numberOfInputNeurons);
			
			//logger.info("Numer of numberOfInputNeurons"+numberOfInputNeurons);
			//logger.info("Numer of numberOfInnerNeurons"+numberOfInnerNeurons);
			//logger.info("actConsArray: "+actConsArray.length);
			searched=new NetworkArchitecture(numberOfInputNeurons,numberOfInnerNeurons,actConsArray);
			
			//Test the Network validity
			if(searched.isValid()){
				//Add the architecture in the list
				
				addNetworkArchitecture(searched);
				return searched;
			}
			else{
				if(loggerOn)
					logger.info("Archi no valid");
			}
		}
		
		
		return null;
	}
	

	
	private synchronized void addNetworkArchitecture(NetworkArchitecture architecture){
		networkArchitectures.add(architecture);
	}
	
	public synchronized OptimizationResults getOptResults(int dimension){
		if(!netArchiOptResultMap.containsKey(dimension)){
			netArchiOptResultMap.put(dimension, new OptimizationResults() );
		}
		return netArchiOptResultMap.get(dimension);
	}
	
	
	public LinkedList<String> getInputNeuronNames(){
		
		LinkedList<String> names=new LinkedList<String>();
		if(dayOfWeekActivated){
			names.add(FIELD_DayOfWeek);
		}
		for(TimeSeries series:this.getSortedTimeSeries()){
			names.addAll(series.getInputNeuronNames());
		}
		
		return names;
	}

	public DataSet getTrainingDataSet(){
		
		if(trainingSet!=null)return trainingSet;
		
		//Create the input arrays
		LinkedList<double[]> doubleArrayList=new LinkedList<double[]>();
		LinkedList<TimeSeries> sortedTimeSeries=this.getSortedTimeSeries();
		if(dayOfWeekActivated){
			createDayOfWeekSeries(sortedTimeSeries);
		}
		for(TimeSeries series:sortedTimeSeries){
			doubleArrayList.addAll(series.transformSeriesToDoubleArrayList(lastInputPointDate));
		}
		
		//Create the output array
		double[] outputArray=createOutputArray(doubleArrayList.get(0).length);
		
		//Create the Training set
		trainingSet = new DataSet(doubleArrayList.size(), 1);
		
		for(int i=0;i<doubleArrayList.get(0).length;i++){
			
			//TODO delete
			if(i>600)break;
			double[] input=new double[doubleArrayList.size()];
			double[] output=new double[]{outputArray[i]};
			
			for(int j=0;j<doubleArrayList.size();j++){
				input[j]=doubleArrayList.get(j)[i];
			}
			
			 trainingSet.addRow(new DataSetRow(input, output));
		}
		
		//Normalize the training set
		trainingSet.normalize();
		
		numberOfInputNeurons=trainingSet.getRowAt(0).getInput().length;
		
		return trainingSet;
	}
	
	
	public int getNumberOfInputNeurons(){
		if(trainingSet==null)
			getTrainingDataSet();
		
		return numberOfInputNeurons;
	}
	
	
	private void createDayOfWeekSeries(LinkedList<TimeSeries> sortedTimeSeries){
		TimeSeries dayOfWeekSerie=new TimeSeries(FIELD_DayOfWeek, TimeSeriesCategory.RATE);
		dayOfWeekSerie.setNumberOfPastValues(1);
		for(ValuePoint point:sortedTimeSeries.getFirst().getInputValues()){
			double dayofWeek=(double)point.getDate().get(Calendar.DAY_OF_WEEK);
			dayOfWeekSerie.getInputValues().add(new ValuePoint(point.getDate(), dayofWeek));
		}
		sortedTimeSeries.addFirst(dayOfWeekSerie);
	}
	
	private double[] createOutputArray(int maxNumberOfValues){
		double[] outputArray=new double[maxNumberOfValues];
		double[] tt=outputPointList.toDoubleArray();
		int diff=outputPointList.toDoubleArray().length-maxNumberOfValues;
		for(int i=tt.length-1;i>=0;i--){
			if(i-diff<0)break;
			outputArray[i-diff]=tt[i];
		}
		
		return outputArray;
		
	}
	
	
	
	public Type getOptimizationResultType(){
		switch (period) {
		case DAY:
			return Type.NEURAL_NETWORK_OUTPUT_DAY;
		case HOUR:
			return Type.NEURAL_NETWORK_OUTPUT_HOUR;
		case MINUTE:
			return Type.NEURAL_NETWORK_OUTPUT_MINUTE;
		case SECONDE:
			return Type.NEURAL_NETWORK_OUTPUT_SECONDE;

		default:
			return Type.NEURAL_NETWORK_OUTPUT_DAY;
		}
	}
	
	
	public Calendar getLastInputPointDate() {
		return lastInputPointDate;
	}

	public void setLastInputPointDate(Calendar lastInputPointDate) {
		if(this.lastInputPointDate==null){
			this.lastInputPointDate=lastInputPointDate;return;
		}
	
		if(this.lastInputPointDate.getTimeInMillis()<lastInputPointDate.getTimeInMillis()){
			this.lastInputPointDate=lastInputPointDate;return;
		}
	
	}
	

	public AlgorithmParameters<boolean[]> getOptArchitectureParam() {
		return optArchitectureParam;
	}

	public LearnParameters getLearnParam() {
		return learnParam;
	}
	
	

	public void setOptArchitectureParam(AlgorithmParameters<boolean[]> optArchitectureParam) {
	this.optArchitectureParam = optArchitectureParam;
	}
	

	public void setLearnParam(LearnParameters learnParam) {
		
		this.learnParam = learnParam;
		
	}
	

	public AlgorithmParameters<double[]> getOptLearnParam() {
		return optLearnParam;
	}

	public void setOptLearnParam(AlgorithmParameters<double[]> optLearnParam) {
	changes.firePropertyChange(FIELD_OptLearnParam, this.optLearnParam, this.optLearnParam = optLearnParam);}
	

	public ValuePointList getOutputPointList() {
		return outputPointList;
	}

	public void setOutputPointList(ValuePointList outputPointList) {
	changes.firePropertyChange(FIELD_OutputPointList, this.outputPointList, this.outputPointList = outputPointList);}
	

	public boolean isDayOfWeekActivated() {
		return dayOfWeekActivated;
	}

	public void setDayOfWeekActivated(boolean dayOfWeekActivated) {
	changes.firePropertyChange(FIELD_DayOfWeekActivated, this.dayOfWeekActivated, this.dayOfWeekActivated = dayOfWeekActivated);}
	

	public PeriodType getPeriod() {
		return period;
	}

	public void setPeriod(PeriodType period) {
	changes.firePropertyChange(FIELD_Period, this.period, this.period = period);}
	
	public String getName() {
		return Name;
	}

	public void setName(String name) {
		changes.firePropertyChange(FIELD_Name, this.Name, this.Name = name);
	}

	public Calendar getLastUpdate() {
		return lastUpdate;
	}

	public void setLastUpdate(Calendar lastUpdate) {
	changes.firePropertyChange(FIELD_LastUpdate, this.lastUpdate, this.lastUpdate = lastUpdate);}
	

	public LinkedList<TimeSeries> getAllTimeSeries() {
		return allTimeSeries;
	}
	
	public LinkedList<TimeSeries> getSortedTimeSeries(){
		LinkedList<TimeSeries> list=new LinkedList<TimeSeries>();
		for(TimeSeries series:this.getTimeSeriesFromCategory(TimeSeriesCategory.RATE)){
			list.add(series);
		}
		for(TimeSeries series:this.getTimeSeriesFromCategory(TimeSeriesCategory.FINANCIAL)){
			list.add(series);
		}
		return list;
	}
	
	
	
	public LinkedList<TimeSeries> getTimeSeriesFromCategory(TimeSeriesCategory category){
		LinkedList<TimeSeries> list=new LinkedList<TimeSeries>();
		for(TimeSeries series:this.getAllTimeSeries()){
			if(series.getCategory()!=category)continue;
			list.add(series);
		}
		
		return list;
		
	}

	public void setAllTimeSeries(LinkedList<TimeSeries> allTimeSeries) {
	changes.firePropertyChange(FIELD_AllTimeSeries, this.allTimeSeries, this.allTimeSeries = allTimeSeries);}
	

	@Override
	protected void initAttribute(Element rootElement) {
		
		this.setName(rootElement.getAttribute(FIELD_Name));
		this.setLastUpdate(DateTool.StringToDate(rootElement.getAttribute(FIELD_LastUpdate)));
		
		this.setPeriod(PeriodType.fromString((rootElement.getAttribute(FIELD_Period))));
		this.setDayOfWeekActivated(Boolean.getBoolean(rootElement.getAttribute(FIELD_DayOfWeekActivated)));
		
		allTimeSeries.clear();
		networkArchitectures.clear();
		netArchiOptResultMap.clear();
	}

	@Override
	protected void initChild(Element childElement) {
		TimeSeries ent=new TimeSeries();
		NetworkArchitecture arch=new NetworkArchitecture();
		OptimizationResults results=new OptimizationResults();
		if(childElement.getTagName().equals(ent.getTagName())){
			ent.init(childElement);
			allTimeSeries.add(ent);
		}
		else if(childElement.getTagName().equals(outputPointList.getTagName())){
			outputPointList.init(childElement);
		}
		else if(childElement.getTagName().equals(optLearnParam.getTagName())){
			optLearnParam.init(childElement);
		}
		else if(childElement.getTagName().equals(learnParam.getTagName())){
			learnParam.init(childElement);
		}
		else if(childElement.getTagName().equals(optArchitectureParam.getTagName())){
			optArchitectureParam.init(childElement);
		}
		else if(childElement.getTagName().equals(arch.getTagName())){
			arch.init(childElement);
			networkArchitectures.add(arch);
		}
		else if(childElement.getTagName().equals(results.getTagName())){
			results.init(childElement);
			if(results.getBestResult()!=null)
				netArchiOptResultMap.put(results.getBestResult().getGenome().size(), results);
		}
		
	}

	@Override
	protected void setAttribute(Element rootElement) {
		rootElement.setAttribute(FIELD_Name,this.getName());
		rootElement.setAttribute(FIELD_LastUpdate,DateTool.dateToString( this.getLastUpdate()));
		
		rootElement.setAttribute(FIELD_Period,PeriodType.toString(this.getPeriod()));
		rootElement.setAttribute(FIELD_DayOfWeekActivated,String.valueOf(this.isDayOfWeekActivated()));
		
	}

	@Override
	protected void appendChild(Element rootElement, Document doc) {
		for(TimeSeries ent:allTimeSeries){
			rootElement.appendChild(ent.toDomElement(doc));
		}
		rootElement.appendChild(outputPointList.toDomElement(doc));
		rootElement.appendChild(optLearnParam.toDomElement(doc));
		rootElement.appendChild(learnParam.toDomElement(doc));
		rootElement.appendChild(optArchitectureParam.toDomElement(doc));
		
		for(NetworkArchitecture ent:networkArchitectures){
			rootElement.appendChild(ent.toDomElement(doc));
		}
		
		for(OptimizationResults results:this.netArchiOptResultMap.values()){
			rootElement.appendChild(results.toDomElement(doc));
		}
		
		
	}

}
