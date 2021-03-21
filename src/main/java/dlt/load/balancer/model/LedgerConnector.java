package dlt.load.balancer.model;

import dlt.client.tangle.model.Transaction;
import dlt.client.tangle.services.ILedgerReader;
import dlt.client.tangle.services.ILedgerWriter;

/**
 *
 * @author Uellington Damasceno
 * @version 0.0.1
 */
public class LedgerConnector {
    private ILedgerReader ledgerReader;
    private ILedgerWriter ledgerWriter;
    private String newMessage;
    private String lastMessage;

    public void setLedgerWriter(ILedgerWriter ledgerWriter){
        this.ledgerWriter = ledgerWriter;
        System.out.println("Injetou escrita");
    }
    
    public void setLedgerReader(ILedgerReader ledgerReader){
        this.ledgerReader = ledgerReader;
        System.out.println("Injetou Leitura");

    }
    
    
    public boolean isInterrupted() {
    	return this.ledgerReader.getDLTInboundMonitor().isInterrupted();
    }
    
    public String getMessage() {
    		
    		this.newMessage = this.ledgerReader.getMessage();
    		if(this.newMessage!=null && !this.newMessage.equals(this.lastMessage)) {
    			
    			this.lastMessage = this.newMessage;
    			this.newMessage = null;
        			
    		}
    		return this.lastMessage;
    	
    }
    
    public Transaction getTransactionByHash(String hashTransaction) {
    	
    	return this.ledgerWriter.getTransactionByHash(hashTransaction);
    }
    
    public void put (Transaction transaction) throws InterruptedException {
    	this.ledgerWriter.put(transaction);
    }
    
    
    
    
    
    
}
