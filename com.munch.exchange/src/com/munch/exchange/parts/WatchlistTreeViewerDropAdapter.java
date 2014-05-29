package com.munch.exchange.parts;

import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerDropAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.TransferData;

import com.munch.exchange.model.core.watchlist.Watchlist;
import com.munch.exchange.model.core.watchlist.WatchlistEntity;
import com.munch.exchange.services.IWatchlistProvider;

public class WatchlistTreeViewerDropAdapter extends ViewerDropAdapter {
	
	private final Viewer viewer;
	
	private WatchlistTreeContentProvider contentProvider;
	
	private IWatchlistProvider watchlistProvider;
	
	private int location;
	
	public WatchlistTreeViewerDropAdapter(Viewer viewer,WatchlistTreeContentProvider contentProvider,
			IWatchlistProvider watchlistProvider){
		super(viewer);
		this.viewer = viewer;
		this.contentProvider = contentProvider;
		this.watchlistProvider = watchlistProvider;
	}
	
	@Override
	public void drop(DropTargetEvent event) {
		location = this.determineLocation(event);
	    //String target = (String) determineTarget(event);
		
	    String translatedLocation ="";
	    switch (location){
	    case 1 :
	      translatedLocation = "Dropped before the target ";
	      break;
	    case 2 :
	      translatedLocation = "Dropped after the target ";
	      break;
	    case 3 :
	      translatedLocation = "Dropped on the target ";
	      break;
	    case 4 :
	      translatedLocation = "Dropped into nothing ";
	      break;
	    }
	    
	    System.out.println(translatedLocation);
	   // System.out.println("The drop was done on the element: " + target);

		super.drop(event);
	}
	
	@Override
	public boolean performDrop(Object data) {
		// TODO Auto-generated method stub
		/*
		if(location==3 && group!=null){
			
			group.addRole(data.toString());
			
			if(provider.updateData(group)!=null){
				broker.send(IBrokerEvents.USER_GROUP_UPDATE, group);
			}
			
		}
		*/
		
		
		boolean rateAdded=false;
		
		String[] uuidArray=data.toString().split(";");
		for(int i=0;i<uuidArray.length;i++){
			if(uuidArray[i].isEmpty())continue;
			
			boolean inList=false;
			for(WatchlistEntity ent : contentProvider.getCurrentList().getList()){
				if(ent.getRateUuid().equals(uuidArray[i])){
					inList=true;break;
				}
			}
			
			if(inList)continue;
			
			WatchlistEntity ent=new WatchlistEntity();
			ent.setRateUuid(uuidArray[i]);
			contentProvider.getCurrentList().getList().add(ent);
			
			rateAdded=true;
			
		}
		
		
		if(rateAdded && viewer!=null){
			watchlistProvider.save();
			viewer.refresh();
		}
		
		return rateAdded;
	}

	@Override
	public boolean validateDrop(Object target, int operation,
			TransferData transferType) {
		// TODO Auto-generated method stub
		/*
		if(target instanceof UserClientGroup ){
			
			UserClientGroup g=(UserClientGroup) target;
			
			if( !g.getName().equals(UsersPart.ALL_USERS_GROUP)){
			
			group=g.getGroup();
			return true;
			}
		}
		
		group=null;
		*/
		return true;
	}

}
