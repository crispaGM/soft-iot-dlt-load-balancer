package dlt.load.balancer.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import dlt.auth.services.IDevicePropertiesManager;
import dlt.client.tangle.enums.TransactionType;
import dlt.client.tangle.model.Transaction;
import dlt.load.monitor.model.CpuUsage;
import extended.tatu.wrapper.model.Device;

/**
 *
 * @author Uellington Damasceno
 * @version 0.0.1
 */
public class Balancer  implements Runnable{
    private LedgerConnector connector;
    private String tag;
    private Integer id;
    private boolean lastStatusOverload = false;
    private Long lasReplyTransactionTimestamp; 
    private GSource gSource;
    private Long timoutLbReply;
    private Long timeoutGateway;
    private List<Device> devices = new ArrayList<Device>();
    private IDevicePropertiesManager deviceManager;
    private Device deviceAEnviar;
    Logger logger;
    FileHandler fh;

    private Transaction lastTransaction;

   
    public Balancer(Long timoutLb, Long timoutGateway,Integer idGateway, String tagGateway) {
    	this.timoutLbReply = timoutLb;
    	this.timeoutGateway = timoutGateway;
    	this.id = idGateway;
    	this.tag = tagGateway;
    }
    
    
    public void setDeviceManager(IDevicePropertiesManager deviceManager) {
        this.deviceManager = deviceManager;
    }
    
    
    public void setConnector(LedgerConnector connector){
        this.connector = connector;
    	System.out.println("Injeção sucesso");

    }

    public void start() {
    	
    	run(); 
    }

    public void stop() {
   
    }
    public void initLogger() {
        logger = Logger.getLogger("Processor Log");  
           try {
   			fh = new FileHandler("ProcessorLogFile.log",true);
   		} catch (SecurityException e) {
   			// TODO Auto-generated catch block
   			e.printStackTrace();
   		} catch (IOException e) {
   			// TODO Auto-generated catch block
   			e.printStackTrace();
   		}  
           logger.addHandler(fh);
           SimpleFormatter formatter = new SimpleFormatter();  
           fh.setFormatter(formatter); 

    }
	@Override
	public void run() {
		String gatewayId = this.tag+this.id;
		try {
			devices = deviceManager.getAllDevices();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		while(true) {
			String hashTransaction = this.connector.getMessage();
			Transaction transaction = this.connector.getTransactionByHash(hashTransaction);
		
			if(lastTransaction!=null) {
				try {
					processTransactions(gatewayId, transaction);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
			}else {
				if(transaction.getType().equals(TransactionType.LB_ENTRY)) {
					if(transaction.getSource().equals(gatewayId)) {
						this.lastTransaction = transaction;
					}else {
						
						try {
							Transaction transactionReply = new Transaction();
							transactionReply.setSource(this.tag+this.id);
							transactionReply.setTarget(transaction.getTarget());
							transactionReply.setTimestamp(System.currentTimeMillis());
							transactionReply.setType(TransactionType.LB_ENTRY_REPLY);
							this.lastTransaction = transaction;
							this.connector.put(transactionReply);
						} catch (InterruptedException e) {
				            logger.info("Error commit transaction " + e);
						}
					}
				}
			}
    	}
	}

	private void processTransactions(String gatewayId, Transaction transaction) throws InterruptedException {
		if(lastTransaction.getType().equals(TransactionType.LB_ENTRY)) {
			 if(transaction.getType().equals(TransactionType.LB_ENTRY_REPLY) && !transaction.getSource().equals(gatewayId)) {
				
				try {
					Transaction transactionReply = new Transaction();
					transactionReply.setSource(this.tag+this.id);
					transactionReply.setTarget(transaction.getTarget());
					transactionReply.setTimestamp(System.currentTimeMillis());
					transactionReply.setType(TransactionType.LB_REQUEST);
					this.deviceAEnviar = devices.get(0);
					transactionReply.setDeviceSwapId(deviceAEnviar.getId());
					this.connector.put(transactionReply);
					this.lastTransaction = transaction;
				} catch (InterruptedException e) {
		            logger.info("Error commit transaction " + e);
				}
				
			}else {
				Long timeAtual = System.currentTimeMillis();

				if((timeAtual - lastTransaction.getTimestamp())>= timoutLbReply) {
					try {
						resendTransaction();
					} catch (InterruptedException e) {
			            logger.info("Error commit transaction " + e);

					}
					
				}else {
					return;
				}
				
			}
		}else if(lastTransaction.getType().equals(TransactionType.LB_ENTRY_REPLY)) {
			if(transaction.getType().equals(TransactionType.LB_REQUEST) && !transaction.getSource().equals(gatewayId)) {
				try {
					Transaction transactionReply = new Transaction();
					transactionReply.setSource(this.tag+this.id);
					transactionReply.setTarget(transaction.getTarget());
					transactionReply.setTimestamp(System.currentTimeMillis());
					transactionReply.setType(TransactionType.LB_REPLY);
					this.lastTransaction = transaction;
					this.loadSwapReceberDispositivo(transaction.getDeviceSwapId());
					this.connector.put(transactionReply);
					
				}catch (Exception e) {
		            logger.info("Error commit transaction " + e);
				}
			}else {
				Long timeAtual = System.currentTimeMillis();
				if((timeAtual - lastTransaction.getTimestamp())>= timeoutGateway) 
						lastTransaction = null;
				else {
					return;
				}
			}
		}else if(lastTransaction.getType().equals(TransactionType.LB_REQUEST)) {
			if(transaction.getType().equals(TransactionType.LB_REPLY) && !transaction.getSource().equals(gatewayId)) {
				this.loadSwapEnviarDispositivo();
				this.lastTransaction = transaction;
			}else {
				Long timeAtual = System.currentTimeMillis();
				if((timeAtual - lastTransaction.getTimestamp())>= timeoutGateway) {
					try {
						resendTransaction();
					} catch (InterruptedException e) {
			            logger.info("Error commit transaction " + e);

					}
					
				}else {
					return;
				}
				
			}
		}
	}
	
	public void resendTransaction() throws InterruptedException {
		this.lastTransaction.setTimestamp(System.currentTimeMillis());
    	this.connector.put(lastTransaction);
	}
	
	public void loadSwapEnviarDispositivo(){
		try {
			deviceManager.removeDevice(deviceAEnviar.getId());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
	}
	
	public void loadSwapReceberDispositivo(String deviceId) {
		
		try {
			Optional<Device> retorno = deviceManager.getDevice(deviceId);
			if(retorno.isPresent()) {
				Device deviceAReceber = retorno.get();
				deviceManager.addDevice(deviceAReceber);
				

			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}
	
	protected void updateBrokerStatus(Transaction transaction) throws InterruptedException {
	        this.connector.put(transaction);
	 }   
 }
