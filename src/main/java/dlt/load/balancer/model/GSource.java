package dlt.load.balancer.model;

public class GSource {

    private Long lasReplyTransactionTimestamp; 
    private String sourceId;
    
    
    
    public GSource() {
    	
    }


	public Long getLasReplyTransactionTimestamp() {
		return lasReplyTransactionTimestamp;
	}


	public void setLasReplyTransactionTimestamp(Long lasReplyTransactionTimestamp) {
		this.lasReplyTransactionTimestamp = lasReplyTransactionTimestamp;
	}


	public String getSourceId() {
		return sourceId;
	}


	public void setSourceId(String sourceId) {
		this.sourceId = sourceId;
	}

    
	
	
}
