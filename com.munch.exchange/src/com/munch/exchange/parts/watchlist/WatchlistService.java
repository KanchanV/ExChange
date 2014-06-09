package com.munch.exchange.parts.watchlist;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

import com.munch.exchange.job.objectivefunc.BollingerBandObjFunc;
import com.munch.exchange.model.core.DatePoint;
import com.munch.exchange.model.core.EconomicData;
import com.munch.exchange.model.core.ExchangeRate;
import com.munch.exchange.model.core.historical.HistoricalPoint;
import com.munch.exchange.model.core.limit.OrderTrigger;
import com.munch.exchange.model.core.optimization.OptimizationResults;
import com.munch.exchange.model.core.optimization.OptimizationResults.Type;
import com.munch.exchange.model.core.quote.QuotePoint;
import com.munch.exchange.model.core.watchlist.Watchlist;
import com.munch.exchange.model.core.watchlist.WatchlistEntity;
import com.munch.exchange.parts.composite.RateChart;
import com.munch.exchange.parts.composite.RateChartBollingerBandsComposite;
import com.munch.exchange.services.IExchangeRateProvider;
import com.munch.exchange.services.IQuoteProvider;
import com.munch.exchange.services.IWatchlistProvider;

public class WatchlistService {
	
	@Inject
	IWatchlistProvider watchlistProvider;
	
	@Inject
	IQuoteProvider quoteProvider;
	
	@Inject
	IExchangeRateProvider rateProvider;
	
	@Inject
	WatchlistService(){
		
	}
	
	
	/**
	 * if not loaded the quote will be loaded
	 */
	private QuotePoint searchLastQuote(WatchlistEntity entity){
		if(entity.getRate()==null)return null;
		
		if(entity.getRate().getRecordedQuote().isEmpty() && !(entity.getRate() instanceof EconomicData)){
			quoteProvider.load(entity.getRate());
		}
		
		if(!entity.getRate().getRecordedQuote().isEmpty()){
			QuotePoint point = (QuotePoint) entity.getRate().getRecordedQuote().getLast();
			return point;
		}
		return null;
	}
	
	
	public void refreshQuote(WatchlistEntity entity){
		entity.setLastQuote(searchLastQuote(entity));
		if(entity.getBollingerBandTrigger()!=null){
			entity.getBollingerBandTrigger().setValue(entity.getLastQuote().getLastTradePrice());
		}
	}
	
	public void refreshHistoricalData(WatchlistEntity entity,Calendar startWatchDate){
		if(entity.getRate()!=null && !entity.getRate().getHistoricalData().isEmpty()){
			//Buy and Old
			entity.setBuyAndOld(entity.getRate().getHistoricalData().calculateKeepAndOld(startWatchDate, DatePoint.FIELD_Close));
			//Max Profit
			entity.setMaxProfit(entity.getRate().getHistoricalData().calculateMaxProfit(startWatchDate, DatePoint.FIELD_Close));
			//Bollinger Band
			System.out.println("Refrsh Hist data: "+entity.getRate().getFullName());
			
			BollingerBandObjFunc func=this.getBollingerBandObjFunc(entity.getRate(),startWatchDate);
			if(func!=null ){
				
				double v=func.compute(entity.getRate());
				double profit=func.getMaxProfit()- v;
				OrderTrigger trigger=new OrderTrigger(entity.getLastQuote().getLastTradePrice(), profit, func.getLimitRange());
				entity.setBollingerBandTrigger(trigger);
			}
			else{
				
			}
			
		}
	}
	
	
	
	public List<WatchlistEntity> findAllWatchlistEntities(String uuid){
		List<WatchlistEntity> list=new LinkedList<WatchlistEntity>();
		
		for(Watchlist watchlist:this.watchlistProvider.load().getLists()){
			for(WatchlistEntity ent:watchlist.getList()){
				if(ent.getRateUuid().equals(uuid)){
					list.add(ent);
				}
			}
		}
		
		return list;
	}
	
	
	
	public WatchlistEntity findEntityFromList(Watchlist watchlist,String uuid){
		
		
			for(WatchlistEntity ent:watchlist.getList()){
				if(ent.getRateUuid().equals(uuid)){
					return ent;
				}
			}
		
		
		return null;
	}
	
	
	public BollingerBandObjFunc getBollingerBandObjFunc(ExchangeRate rate, Calendar startWatchDate ){
		if(rate!=null 
				&& !rate.getHistoricalData().isEmpty()
				//&& rate.getOptResultsMap().get(Type.BILLINGER_BAND)!=null
				&& !rate.getOptResultsMap().get(Type.BILLINGER_BAND).getResults().isEmpty()){
			
			float maxProfit=rate.getHistoricalData().calculateMaxProfit(startWatchDate, DatePoint.FIELD_Close);
			
			//Create the Bollinger Band function
			BollingerBandObjFunc bollingerBandObjFunc=new BollingerBandObjFunc(
					 HistoricalPoint.FIELD_Close,
					 RateChart.PENALTY,
					 rate.getHistoricalData().getNoneEmptyPoints(),
					 RateChartBollingerBandsComposite.maxNumberOfDays,
					 RateChartBollingerBandsComposite.maxBandFactor,
					 maxProfit
					 );
			bollingerBandObjFunc.setPeriod(rate.getHistoricalData().calculatePeriod(startWatchDate));
			
			//double[] g=entity.getRate().getOptResultsMap().get(Type.BILLINGER_BAND).getResults().getFirst().getDoubleArray();
			//double v=bollingerBandObjFunc.compute(g, null);
			//float profit=maxProfit- (float)v;
			//System.out.println("opt results found!: "+rate.getFullName());
			return bollingerBandObjFunc;
			
		}
		/*
		System.out.println("No opt results!: "+rate.getFullName());
		if( rate.getOptResultsMap()!=null){
			for(OptimizationResults.Type type:rate.getOptResultsMap().keySet()){
				System.out.println("Types!: "+OptimizationResults.OptimizationTypeToString(type));
				System.out.println("Results!: "+rate.getOptResultsMap().get(type).getResults().size());
				
				
			}
		}
		*/
		
		return null;
	}
	
	
	
	
	

}
