package com.munch.exchange.parts.watchlist;

import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.e4.core.contexts.ContextInjectionFactory;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.ui.di.Focus;
import org.eclipse.e4.ui.di.UIEventTopic;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.DateTime;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;

import com.munch.exchange.IEventConstant;
import com.munch.exchange.job.HistoricalDataLoader;
import com.munch.exchange.model.core.DatePoint;
import com.munch.exchange.model.core.EconomicData;
import com.munch.exchange.model.core.ExchangeRate;
import com.munch.exchange.model.core.quote.QuotePoint;
import com.munch.exchange.model.core.watchlist.Watchlist;
import com.munch.exchange.model.core.watchlist.WatchlistEntity;
import com.munch.exchange.services.IExchangeRateProvider;
import com.munch.exchange.services.IQuoteProvider;
import com.munch.exchange.services.IWatchlistProvider;

import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.widgets.TreeItem;
import org.eclipse.swt.events.MouseTrackAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.DisposeEvent;

public class WatchlistPart {
	
	@Inject
	IEclipseContext context;
	
	@Inject
	Shell shell;
	
	@Inject
	IWatchlistProvider watchlistProvider;
	
	@Inject
	IQuoteProvider quoteProvider;
	
	@Inject
	IExchangeRateProvider rateProvider;
	
	
	//Loader
	HistoricalDataLoader historicalDataLoader;
	
	private WatchlistTreeContentProvider contentProvider;
	private WatchlistService watchlistService;
	private WatchlistViewerComparator comparator;
	
	private Calendar startWatchDate=Calendar.getInstance();
	
	//private Watchlist currentList=null;
	private Combo comboWachtlist;
	private Button btnDelete;
	private TreeViewer treeViewer;
	private TreeViewerColumn treeViewerColumnName;
	private TreeViewerColumn treeViewerColumnPrice;
	private TreeViewerColumn treeViewerColumnChange;
	private DateTime dateTimeWatchPeriod;
	private TreeViewerColumn treeViewerColumnBuyAndOld;
	private TreeViewerColumn treeViewerColumnMaxProfit;
	
	public WatchlistPart() {
	}

	/**
	 * Create contents of the view part.
	 */
	@PostConstruct
	public void createControls(Composite parent) {
		
		contentProvider=ContextInjectionFactory.make( WatchlistTreeContentProvider.class,context);
		watchlistService=ContextInjectionFactory.make( WatchlistService.class,context);
		historicalDataLoader=ContextInjectionFactory.make( HistoricalDataLoader.class,context);
		
		comparator=new WatchlistViewerComparator(watchlistService);
		
		parent.setLayout(new GridLayout(1, false));
		
		Composite compositeHeader = new Composite(parent, SWT.NONE);
		compositeHeader.setLayout(new GridLayout(4, false));
		compositeHeader.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		
		comboWachtlist = new Combo(compositeHeader, SWT.NONE);
		comboWachtlist.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
		comboWachtlist.setText("Empty...");
		fillComboWachtlist();
		comboWachtlist.addModifyListener(new ModifyListener() {
			public void modifyText(ModifyEvent e) {
				if(comboWachtlist.getText().isEmpty())return;
				
				for(Watchlist list:watchlistProvider.load().getLists()){
					list.setSelected(comboWachtlist.getText().equals(list.getName()));
					if(list.isSelected()){
						contentProvider.setCurrentList(list);
						loadNextHistoricalData();
						refreshViewer();
					}
				}
				watchlistProvider.save();
			}
		});
		
		dateTimeWatchPeriod = new DateTime(compositeHeader, SWT.BORDER | SWT.DROP_DOWN | SWT.LONG);
		startWatchDate.add(Calendar.DAY_OF_YEAR, -120);
		comparator.setStartWatchDate(startWatchDate);
		dateTimeWatchPeriod.setDate(startWatchDate.get(Calendar.YEAR), startWatchDate.get(Calendar.MONTH), startWatchDate.get(Calendar.DAY_OF_MONTH));
		dateTimeWatchPeriod.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				System.out.println("Details: "+e.detail);
				startWatchDate.set(	dateTimeWatchPeriod.getYear(),
									dateTimeWatchPeriod.getMonth(),
									dateTimeWatchPeriod.getDay(),
									dateTimeWatchPeriod.getHours(),
									dateTimeWatchPeriod.getMinutes(),
									dateTimeWatchPeriod.getSeconds());
				comparator.setStartWatchDate(startWatchDate);
				refreshViewer();
			}
		});
		dateTimeWatchPeriod.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		
		
		//startWatchDate.get(Calendar.DAY_OF_MONTH)
		
		
		
		
		Button btnNewList = new Button(compositeHeader, SWT.NONE);
		btnNewList.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));
		btnNewList.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				
				InputDialog dlg=new InputDialog(shell, "Create new watchlist", "Enter the new wachtlichname", "new list", null);
				 if (dlg.open() == Window.OK) {
					
					 Watchlist list=watchlistProvider.load().addNewList(dlg.getValue());
					 if(list==null){
						 MessageDialog.openError(shell, "Watchlist error", "Cannot create the watchlist: "+dlg.getValue());
						 return;
					 }
					 
					 //Save the list
					 contentProvider.setCurrentList(list);
					 refreshViewer();
					 //watchlistProvider.save();
					 fillComboWachtlist();
					 
				 }
				
			}
		});
		btnNewList.setText("New List");
		
		btnDelete = new Button(compositeHeader, SWT.NONE);
		btnDelete.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				boolean res=MessageDialog.openQuestion(shell, "Delete current watchlist",
						"Do you want to delete the watchlist \""+contentProvider.getCurrentList().getName()+"\"");
				if (!res) return;
				
				int index=0;
				for(Watchlist list:watchlistProvider.load().getLists()){
					if(list.getName().equals(contentProvider.getCurrentList().getName()))break;
					index++;
				}
				if(watchlistProvider.load().getLists().size()==1){
					watchlistProvider.load().getLists().clear();
					fillComboWachtlist();
					watchlistProvider.save();
					return;
				}
				
				if(watchlistProvider.load().getLists().remove(index)!=null){
					if(!watchlistProvider.load().getLists().isEmpty()){
						contentProvider.setCurrentList(watchlistProvider.load().getLists().getFirst());
						contentProvider.getCurrentList().setSelected(true);
					}
					refreshViewer();
					fillComboWachtlist();
					watchlistProvider.save();
				}
				else{
					 MessageDialog.openError(shell, "Watchlist error", "Cannot delete the watchlist: "+contentProvider.getCurrentList().getName());
				}
				
			}
		});
		btnDelete.setText("Delete");
		btnDelete.setEnabled(!watchlistProvider.load().getLists().isEmpty());
		
		
		//##############################
		//##   Tree viewer definition ##
		//##############################
		
		treeViewer = new TreeViewer(parent, SWT.BORDER| SWT.MULTI
				| SWT.V_SCROLL);
		treeViewer.setContentProvider(contentProvider);
		Tree tree = treeViewer.getTree();
		tree.setLinesVisible(true);
		tree.setHeaderVisible(true);
		tree.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
		
		//Add Drop Support
		int operations = DND.DROP_COPY| DND.DROP_MOVE;
	    Transfer[] transferTypes = new Transfer[]{TextTransfer.getInstance()};
	    treeViewer.addDropSupport(operations, transferTypes, 
	    new WatchlistTreeViewerDropAdapter(treeViewer,contentProvider,watchlistProvider,rateProvider));
		
	    treeViewer.setInput(contentProvider.getCurrentList());
	    treeViewer.setComparator(comparator);
	    
	    
		//##############################
		//##          Columns         ##
		//##############################	    
	    
		treeViewerColumnName = new TreeViewerColumn(treeViewer, SWT.NONE);
		treeViewerColumnName.setLabelProvider(new NameColumnLabelProvider());
		TreeColumn trclmnName = treeViewerColumnName.getColumn();
		trclmnName.setWidth(300);
		trclmnName.setText("Name");
		trclmnName.addSelectionListener(getSelectionAdapter(trclmnName, 0));
		
	    
	    treeViewerColumnPrice = new TreeViewerColumn(treeViewer, SWT.NONE);
	    treeViewerColumnPrice.setLabelProvider(new PriceColumnLabelProvider());
	    TreeColumn trclmnPrice = treeViewerColumnPrice.getColumn();
	    trclmnPrice.setAlignment(SWT.CENTER);
	    trclmnPrice.setWidth(100);
	    trclmnPrice.setText("Price");
	    trclmnPrice.addSelectionListener(getSelectionAdapter(trclmnPrice, 1));
	    
	    treeViewerColumnChange = new TreeViewerColumn(treeViewer, SWT.NONE);
	    treeViewerColumnChange.setLabelProvider(new ChangeColumnLabelProvider());
	    TreeColumn trclmnChange = treeViewerColumnChange.getColumn();
	    trclmnChange.setAlignment(SWT.RIGHT);
	    trclmnChange.setWidth(100);
	    trclmnChange.setText("Change");
	    trclmnChange.addSelectionListener(getSelectionAdapter(trclmnChange, 2));
	    
	    treeViewerColumnBuyAndOld = new TreeViewerColumn(treeViewer, SWT.NONE);
	    treeViewerColumnBuyAndOld.setLabelProvider(new BuyAndOldColumnLabelProvider());
	    TreeColumn trclmnBuyandold = treeViewerColumnBuyAndOld.getColumn();
	    trclmnBuyandold.setAlignment(SWT.RIGHT);
	    trclmnBuyandold.setWidth(110);
	    trclmnBuyandold.setText("Buy and old");
	    trclmnBuyandold.addSelectionListener(getSelectionAdapter(trclmnBuyandold, 3));
	    
	    treeViewerColumnMaxProfit = new TreeViewerColumn(treeViewer, SWT.NONE);
	    treeViewerColumnMaxProfit.setLabelProvider(new MaxProfitColumnLabelProvider());
	    TreeColumn trclmnNewColumn = treeViewerColumnMaxProfit.getColumn();
	    trclmnNewColumn.setAlignment(SWT.RIGHT);
	    trclmnNewColumn.setWidth(100);
	    trclmnNewColumn.setText("Max profit");
	    trclmnNewColumn.addSelectionListener(getSelectionAdapter(trclmnNewColumn, 4));

	    refreshViewer();
	}
	
	private void refreshViewer(){
		if(treeViewer!=null){
			treeViewer.setInput(contentProvider.getCurrentList());
			treeViewer.refresh();
		}
	}
	
	private void fillComboWachtlist(){
		comboWachtlist.removeAll();
		if(btnDelete!=null){
			btnDelete.setEnabled(!watchlistProvider.load().getLists().isEmpty());
		}
		
		if(watchlistProvider.load().getLists().isEmpty()){
			comboWachtlist.setText("Empty..");
			return;
		}
		
		
		//comboWachtlist.clearSelection();
		
		for(Watchlist list: watchlistProvider.load().getLists()){
			comboWachtlist.add(list.getName());
			if(list.isSelected()){
				contentProvider.setCurrentList(list);
				comboWachtlist.setText(list.getName());
			}
		}
		
		
	}
	
	
	private void loadNextHistoricalData(){
		WatchlistEntity toLoad=null;
		for(WatchlistEntity ent:this.contentProvider.getCurrentList().getList()){
			if(ent.getRate()==null)continue;
			if(ent.getRate().getHistoricalData().isEmpty())
				toLoad=ent;
		}
		
		if(toLoad!=null){
			historicalDataLoader.setRate(toLoad.getRate());
			historicalDataLoader.schedule();
		}
	}
	
	 
	//################################
	//##       Event Reaction       ##
	//################################
	
	
	@Inject
	private void loadedRate(@Optional  @UIEventTopic(IEventConstant.RATE_LOADED) ExchangeRate rate ){
		if(treeViewer!=null && rate!=null){
			
			List<WatchlistEntity> list=watchlistService.findAllWatchlistEntities(rate.getUUID());
			for(WatchlistEntity ent:list)ent.setRate(rate);
			if(list.size()>0)
				treeViewer.refresh();
			
			boolean areAllLoaded=true;
			for(WatchlistEntity ent:contentProvider.getCurrentList().getList()){
				if(ent.getRate()==null)areAllLoaded=false;
			}
			if(areAllLoaded)
				loadNextHistoricalData();
		}
	}
	
	
	@Inject
	private void quoteLoaded(@Optional  @UIEventTopic(IEventConstant.QUOTE_LOADED) String rate_uuid ){
		
		if(!isReadyToReact(rate_uuid)){return;}
		
		List<WatchlistEntity> list=watchlistService.findAllWatchlistEntities(rate_uuid);
		if(list.size()>0){
			treeViewer.refresh();
		}
	}
	
	@Inject
	private void quoteUpdated(@Optional  @UIEventTopic(IEventConstant.QUOTE_UPDATE) String rate_uuid ){
		
		if(!isReadyToReact(rate_uuid)){return;}
		
		List<WatchlistEntity> list=watchlistService.findAllWatchlistEntities(rate_uuid);
		if(list.size()>0){
			treeViewer.refresh();
		}
	}
	
	@Inject
	private void historicalDataLoaded(@Optional  @UIEventTopic(IEventConstant.HISTORICAL_DATA_LOADED) String rate_uuid ){
		
		if(!isReadyToReact(rate_uuid)){return;}
		
		WatchlistEntity ent=watchlistService.findEntityFromList(contentProvider.getCurrentList(),  rate_uuid);
		if(ent!=null){
			loadNextHistoricalData();
			treeViewer.refresh();
		}
	}
	
	
	private boolean isReadyToReact(String rate_uuid){
		if(rate_uuid==null || rate_uuid.isEmpty()){
			return false;
		}
		if(treeViewer==null)return false;
		
		return true;
	}
	
	@PreDestroy
	public void dispose() {
	}

	@Focus
	public void setFocus() {
		// TODO	Set the focus to control
	}
	
	//################################
	//##     ColumnLabelProvider    ##
	//################################
	
	class NameColumnLabelProvider extends ColumnLabelProvider{
		public Image getImage(Object element) {
			return null;
		}
		public String getText(Object element) {
			if(element instanceof WatchlistEntity){
				WatchlistEntity entity=(WatchlistEntity) element;
				if(entity.getRate()!=null)
					return entity.getRate().getFullName();
				return entity.getRateUuid();
			}
			return element == null ? "" : element.toString();
		}
	}
	
	class PriceColumnLabelProvider extends ColumnLabelProvider{
		public Image getImage(Object element) {
    		return null;
    	}
    	public String getText(Object element) {
    		if(element instanceof WatchlistEntity){
				WatchlistEntity entity=(WatchlistEntity) element;
				QuotePoint point=watchlistService.searchLastQuote(entity);
				if(point!=null){
					return String.valueOf(point.getLastTradePrice());
				}
			}
    		return "loading..";
    	}
	}
	
	class ChangeColumnLabelProvider extends ColumnLabelProvider{
		public Image getImage(Object element) {
    		// TODO Auto-generated method stub
    		return null;
    	}
    	public String getText(Object element) {
    		if(element instanceof WatchlistEntity){
				WatchlistEntity entity=(WatchlistEntity) element;
				QuotePoint point=watchlistService.searchLastQuote(entity);
				if(point!=null){
					float per = point.getChange() * 100 / point.getLastTradePrice();
					return String.format("%.2f", per) + "%";
					
				}
			}
    		return "";
    	}
	}
	
	class BuyAndOldColumnLabelProvider extends ColumnLabelProvider{
		public Image getImage(Object element) {
    		// TODO Auto-generated method stub
    		return null;
    	}
    	public String getText(Object element) {
    		if(element instanceof WatchlistEntity){
				WatchlistEntity entity=(WatchlistEntity) element;
				if(entity.getRate()!=null && !entity.getRate().getHistoricalData().isEmpty()){
					return String.format("%.2f",100*entity.getRate().getHistoricalData().calculateKeepAndOld(startWatchDate, DatePoint.FIELD_Close))+ "%";
				}
			}
    		return "loading...";
    	}
	}
	
	class MaxProfitColumnLabelProvider extends ColumnLabelProvider{
		public Image getImage(Object element) {
    		return null;
    	}
    	public String getText(Object element) {
    		if(element instanceof WatchlistEntity){
				WatchlistEntity entity=(WatchlistEntity) element;
				if(entity.getRate()!=null && !entity.getRate().getHistoricalData().isEmpty()){
					float profit=100*entity.getRate().getHistoricalData().calculateMaxProfit(startWatchDate, DatePoint.FIELD_Close);
					return String.format("%.2f",profit)+ "%";
				}
			}
    		return "loading...";
    	}
	}
	
	
	 private SelectionAdapter getSelectionAdapter(final  TreeColumn  column,
		      final int index) {
		    SelectionAdapter selectionAdapter = new SelectionAdapter() {
		      @Override
		      public void widgetSelected(SelectionEvent e) {
		        comparator.setColumn(index);
		        int dir = comparator.getDirection();
		        treeViewer.getTree().setSortDirection(dir);
		        treeViewer.getTree().setSortColumn(column);
		        treeViewer.refresh();
		      }
		    };
		    return selectionAdapter;
		  }
	
	
}
