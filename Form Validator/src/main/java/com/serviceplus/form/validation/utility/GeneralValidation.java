package com.serviceplus.form.validation.utility;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GeneralValidation {

	  public static boolean isXss(String value) {
	    	String receivedValue=value;
	    	try{
	        if (value != null) {
	        	Pattern scriptPattern =null;
	             List<String> xssWord = new ArrayList<String>(110);
	             xssWord.add("FSCommand");
	             xssWord.add("onAbort");
	             xssWord.add("onActivate");
	             xssWord.add("onAfterPrint");
	             xssWord.add("onAfterUpdate");
	             xssWord.add("onBeforeActivate");
	             xssWord.add("onBeforeCopyfunction");
	             xssWord.add("onBeforeCut");
	             xssWord.add("onBeforeDeactivate");
	             xssWord.add("onBeforeEditFocus");
	             xssWord.add("onBeforePastefunction");
	             xssWord.add("onBeforePrintorexecCommandfunction");
	             xssWord.add("onBeforeUnload");
	             xssWord.add("onBeforeUpdate");
	             xssWord.add("onBegin");
	             xssWord.add("onBlur");
	             xssWord.add("onBounce");
	             xssWord.add("onCellChange");
	             xssWord.add("onChange");
	             xssWord.add("onClick");
	             xssWord.add("onContextMenu");
	             xssWord.add("onControlSelect");
	             xssWord.add("onCopycommand");
	             xssWord.add("onCutcommand");
	             xssWord.add("onDataAvailable");
	             xssWord.add("onDataSetChanged");
	             xssWord.add("onDataSetComplete");
	             xssWord.add("onDblClick");
	             xssWord.add("onDeactivate");
	             xssWord.add("onDrag");
	             xssWord.add("onDragEnd");
	             xssWord.add("onDragLeave");
	             xssWord.add("onDragEnter");
	             xssWord.add("onDragOver");
	             xssWord.add("onDragDrop");
	             xssWord.add("onDragStart");
	             xssWord.add("onDrop");
	             //xssWord.add("onEnd");
	             xssWord.add("onErrorUpdate");
	             xssWord.add("onerror");	             
	             xssWord.add("onFilterChange");
	             xssWord.add("onFinish");
	             xssWord.add("onFocus");
	             xssWord.add("onFocusIn");
	             xssWord.add("onFocusOut");
	             xssWord.add("onHashChange");
	             xssWord.add("onHelp");
	             xssWord.add("onInput");
	             xssWord.add("onKeyDown");
	             xssWord.add("onKeyPress");
	             xssWord.add("onKeyUp");
	             xssWord.add("onLayoutComplete");
	             xssWord.add("onLoad");
	             xssWord.add("onLoseCapture");
	             xssWord.add("onMediaComplete");
	             xssWord.add("onMediaError");
	             xssWord.add("onMessage");
	             xssWord.add("onMouseDown");
	             xssWord.add("onMouseEnter");
	             xssWord.add("onMouseLeave");
	             xssWord.add("onMouseMove");
	             xssWord.add("onMouseOut");
	             xssWord.add("onMouseOver");
	             xssWord.add("onMouseUp");
	             xssWord.add("onMouseWheel");
	             xssWord.add("onMove");
	             xssWord.add("onMoveEnd");
	             xssWord.add("onMoveStart");
	             xssWord.add("onOffline");
	             xssWord.add("onOnline");
	             xssWord.add("onOutOfSync");
	             xssWord.add("onPaste");
	             xssWord.add("onPause");
	             xssWord.add("onPopState");
	             xssWord.add("onProgress");
	             xssWord.add("onPropertyChange");
	             xssWord.add("onReadyStateChange");
	             //xssWord.add("onRedo");
	             xssWord.add("onRepeat");
	             xssWord.add("onReset");
	             xssWord.add("onResize");
	             xssWord.add("onResizeEnd");
	             xssWord.add("onResizeStart");
	             xssWord.add("onResume");
	             xssWord.add("onReverse");
	             xssWord.add("onRowsEnter");
	             xssWord.add("onRowExit");
	             xssWord.add("onRowDelete");
	             xssWord.add("onRowInserted");
	             xssWord.add("onScroll");
	             xssWord.add("onSeek");
	             xssWord.add("onSelect");
	             xssWord.add("onSelectionChange");
	             xssWord.add("onSelectStart");
	             xssWord.add("onStart");
	             xssWord.add("onStop");
	             xssWord.add("onStorage");
	             xssWord.add("onSyncRestored");
	             xssWord.add("onSubmit");
	             xssWord.add("onTimeError");
	             xssWord.add("onTrackChange");
	             //xssWord.add("onUndo");
	             xssWord.add("onUnload");
	             xssWord.add("onURLFlip");
	             xssWord.add("seekSegmentTime");
	             xssWord.add("<script>(.*?)</script>");
	             xssWord.add("");
	             xssWord.add("src[\r\n]*=[\r\n]*\\\'(.*?)\\\'");
	             xssWord.add("src[\r\n]*=[\r\n]*\\\"(.*?)\\\"");
	             xssWord.add("</script>");
	             xssWord.add("<script(.*?)>");
	             xssWord.add("eval\\((.*?)\\)");
	             xssWord.add("alert\\((.*?)\\)");
	             xssWord.add("expression\\((.*?)\\)");
	             xssWord.add("javascript:");
	             xssWord.add("vbscript:");
	             xssWord.add("onload(.*?)=");
	             xssWord.add("confirm(.*?)");
	             xssWord.add("prompt(.*?)");
		            for(int i=0; i<xssWord.size(); i++) {
		            	//System.out.println(xssWord.get(i));
		            	scriptPattern = Pattern.compile(xssWord.get(i), Pattern.CASE_INSENSITIVE);
			            value = scriptPattern.matcher(value).replaceAll("");
		            }
	        }
	    	}catch(Exception e){
	    		e.printStackTrace();
	    	}
	    	if(receivedValue!=null && !receivedValue.equals(value)){
	    		return true;
	    	}
	        return false;
	    }
	  
	  public static boolean isValidRegex(String value,String regex) {
			Pattern pattern = Pattern.compile(regex);
			Matcher matcher = pattern.matcher(value);
			return matcher.matches();
	  }
}
