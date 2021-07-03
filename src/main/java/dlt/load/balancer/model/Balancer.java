package dlt.load.balancer.model;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

import org.json.JSONObject;

import com.google.gson.JsonObject;

import dlt.auth.services.IDevicePropertiesManager;
import dlt.client.tangle.enums.TransactionType;
import dlt.client.tangle.model.Transaction;
import dlt.client.tangle.services.ILedgerSubscriber;
import dlt.load.monitor.model.CpuUsage;
import extended.tatu.wrapper.model.Device;
import extended.tatu.wrapper.util.DeviceWrapper;

/**
 *
 * @author Uellington Damasceno
 * @version 0.0.1
 */
public class Balancer  implements Runnable,ILedgerSubscriber{
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
    String gatewayId;
    Logger logger;
    FileHandler fh;

    private Transaction lastTransaction;

   
    public Balancer() {
    	this.timoutLbReply = new Long(10000);
    	this.timeoutGateway = new Long(1000000);
    	this.id = 1;
    	this.tag = "cloud1/fog";
    	gatewayId = "testado";

    }
    
    
    public void setDeviceManager(IDevicePropertiesManager deviceManager) {
    	System.out.println("Device Injetado");

    	this.deviceManager = deviceManager;
    }
    
    
    public void setConnector(LedgerConnector connector){
    	System.out.println("Injeção sucesso");
        this.connector = connector;
    	this.connector.subscribe("sn",this);
    	this.connector.subscribe("tx",this);


    }

    public void start() {
    	System.out.println("START");

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
		try {
			devices = deviceManager.getAllDevices();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    	
	}


	private void messageArrived(String hashTransaction) {
	


//		String hashTransaction = this.connector.getMessage();
		Transaction transaction = this.connector.getTransactionByHash(hashTransaction);
		System.out.println("NOVA MENSAGEM.....");
		

		if(lastTransaction!=null) {
			try {
				System.out.println("PROCESSANDO MENSAGEM.....");

				processTransactions(transaction);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
		}else {
			System.out.println("AGUARDANDO ENTRY");
			System.out.println("ULTIMA MENSAGEM");
			System.out.println(this.lastTransaction!=null?this.lastTransaction.getType().name():"TA NULO");


			if(transaction.getType().equals(TransactionType.LB_ENTRY)) {
				System.out.println("RECEBI LB_ENTRY");
				if(transaction.getSource().equals(gatewayId)) {
					this.lastTransaction = transaction;
				}else {
					
					try {
						Transaction transactionReply = new Transaction();
						transactionReply.setSource(gatewayId);
						transactionReply.setTarget(transaction.getSource());
						transactionReply.setTimestamp(System.currentTimeMillis());
						transactionReply.setType(TransactionType.LB_ENTRY_REPLY);
						this.lastTransaction = transactionReply;
						this.connector.put(transactionReply);
					} catch (InterruptedException e) {
			            logger.info("Error commit transaction " + e);
					}
				}
			}
		}
	}

	private void processTransactions(Transaction transaction) throws InterruptedException {
		System.out.println("Tipo da Mensagem que chegou");
		System.out.println(transaction.getType().name());
		System.out.println("Utima mensagem");
		System.out.println(lastTransaction.getType().name());
		if(lastTransaction.getType().equals(TransactionType.LB_ENTRY)) {
			 if(transaction.getType().equals(TransactionType.LB_ENTRY_REPLY) && transaction.getTarget().equals(gatewayId)) {
					System.out.println("RECEBI LB_ENTRY_REPLY");

				try {
					Transaction transactionReply = new Transaction();
					transactionReply.setSource(gatewayId);
					transactionReply.setTarget(transaction.getSource());
					transactionReply.setTimestamp(System.currentTimeMillis());
					transactionReply.setType(TransactionType.LB_REQUEST);
					
					String deviceString = new JSONObject(devices.get(0)).toString();
					System.out.println("DEVICE STRING");
					System.out.println(devices.get(0));
					transactionReply.setDeviceSwap(deviceString);
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
			if(transaction.getType().equals(TransactionType.LB_REQUEST) && transaction.getTarget().equals(gatewayId)) {
				System.out.println("RECEBI LB_REQUEST");

				try {
					Transaction transactionReply = new Transaction();
					transactionReply.setSource(gatewayId);
					transactionReply.setTarget(transaction.getSource());
					transactionReply.setTimestamp(System.currentTimeMillis());
					transactionReply.setType(TransactionType.LB_REPLY);
					this.lastTransaction = transaction;
					this.loadSwapReceberDispositivo(transaction.getDeviceSwap());
					this.connector.put(transactionReply);
					
				}catch (Exception e) {
					e.printStackTrace();
				}
			}else {
				Long timeAtual = System.currentTimeMillis();
				if((timeAtual - lastTransaction.getTimestamp())>= timeoutGateway) {
					System.out.println("ANULOOU >>>>>>>>>>>");
					lastTransaction = null;
					
				}
				else {
					return;
				}
			}
		}else if(lastTransaction.getType().equals(TransactionType.LB_REQUEST)) {
			if(transaction.getType().equals(TransactionType.LB_REPLY) && transaction.getTarget().equals(gatewayId)) {
				System.out.println("RECEBI LB_REPLY");

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
			e.printStackTrace();
		}
	
	}
	
	public void loadSwapReceberDispositivo(String device) {
		System.out.println("DEVICE STRING");
		System.out.println(device);
		JSONObject teste  = new JSONObject(device);
		System.out.println(teste);

			Device deviceFromJson = new Device(teste.getString("id"),0, 0, new LinkedList<>());
		try {
//			Optional<Device> retorno = deviceManager.getDevice(deviceId);
//			if(retorno.isPresent()) 
		deviceManager.addDevice(deviceFromJson);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		

	}
	
	protected void updateBrokerStatus(Transaction transaction) throws InterruptedException {
	        this.connector.put(transaction);
	 }


	@Override
	public void update(Object object) {
		
		String hashTransaction = (String) object;
		System.out.println("PRINT OBJECT");
		System.out.println(hashTransaction);
		
		this.messageArrived(hashTransaction);
	}   
 }
