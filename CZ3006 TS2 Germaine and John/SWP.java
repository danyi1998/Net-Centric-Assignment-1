//Germaine and John's code edited from the file provided

/*===============================================================*
 *  File: SWP.java                                               *
 *                                                               *
 *  This class implements the sliding window protocol            *
 *  Used by VMach class					         *
 *  Uses the following classes: SWE, Packet, PFrame, PEvent,     *
 *                                                               *
 *  Author: Professor SUN Chengzheng                             *
 *          School of Computer Engineering                       *
 *          Nanyang Technological University                     *
 *          Singapore 639798                                     *
 *===============================================================*/
import java.util.Timer;
import java.util.TimerTask;

public class SWP 
{

/*========================================================================*
 the following are provided, do not change them!!
 *========================================================================*/
//the following are protocol constants.
public static final int MAX_SEQ = 7; 
public static final int NR_BUFS = (MAX_SEQ + 1)/2;

// the following are protocol variables
private int oldest_frame = 0;
private PEvent event = new PEvent();  
private Packet out_buf[] = new Packet[NR_BUFS];

//the following are used for simulation purpose only
private SWE swe = null;
private String sid = null;  

//Constructor
public SWP(SWE sw, String s)
{
	swe = sw;
	sid = s;
}

//the following methods are all protocol related
private void init()
{
	for (int i = 0; i < NR_BUFS; i++)
	{
		out_buf[i] = new Packet();
        }
}

private void wait_for_event(PEvent e)
{
	swe.wait_for_event(e); //may be blocked
	oldest_frame = e.seq;  //set timeout frame seq
}

private void enable_network_layer(int nr_of_bufs) 
{
	//network layer is permitted to send if credit is available
	swe.grant_credit(nr_of_bufs);
}

private void from_network_layer(Packet p) 
{
	swe.from_network_layer(p);
}

private void to_network_layer(Packet packet) 
{
	swe.to_network_layer(packet);
}

private void to_physical_layer(PFrame fm)  
{
	System.out.println("SWP: Sending frame: seq = " + fm.seq + 
			    " ack = " + fm.ack + " kind = " + 
			    PFrame.KIND[fm.kind] + " info = " + fm.info.data);
	System.out.flush();
	swe.to_physical_layer(fm);
}

private void from_physical_layer(PFrame fm) 
{
	PFrame fm1 = swe.from_physical_layer(); 
	fm.kind = fm1.kind;
	fm.seq = fm1.seq; 
	fm.ack = fm1.ack;
	fm.info = fm1.info;
}


/*===========================================================================*
 	implement your Protocol Variables and Methods below: 
 *==========================================================================*/
	boolean noNak = true; //keep track if an NAK has to be sent
	private Timer normalTimer[] = new Timer[NR_BUFS]; //each packet sent has a retransmission timer  	
	private Timer ackTimer; //to indicate that a separate ack frame needs to be sent
	private static final int retransmissionTimeout = 1000; //retransmit after 1s
	private static final int separateAckTimeout = 500; //send a separate ACK frame after 0.5s

	//increment the sequence number
	private int inc(int seq)
	{
	    return (seq + 1) % (MAX_SEQ + 1); //seq cannot exceed MAX_SEQ
	}
	

	//transmit a frame
	private void sendFrame(int frameKind, int frameNum, int frameExpected, Packet buffer[])
	{
		PFrame frame = new PFrame(); //frame structure  
		frame.kind = frameKind; //set the frame kind

		//if data frame
		if (frameKind == PFrame.DATA)
			frame.info = buffer[frameNum % NR_BUFS]; //get the data from the buffer and put it in the frame

		frame.seq = frameNum; //set the frame sequence number   
		frame.ack = (frameExpected + MAX_SEQ) % (MAX_SEQ + 1); //piggybacked ack of a received frame

		//if frame kind is NAK, set noNAK to false
		if (frameKind == PFrame.NAK)
			noNak = false;    

		to_physical_layer(frame); //send the frame to the physical layer to be transmitted

		//if data frame, start the retransmission timer for that frame
		if (frameKind == PFrame.DATA)
			startTimer(frameNum);

		stopAckTimer(); //ack will be piggybacked, hence stop ack timer to prevent separate ack frame  
	}

	//check if b is in the range of a and c
	private boolean between(int a, int b, int c)
	{
		return ((a <= b) && (b < c)) || ((c < a) && (a <= b)) || ((b < c) && (c < a));
	}

	//start the retransmission timer
	private void startTimer(int seq)
	{
		stopTimer(seq); //stop the previous timer
		normalTimer[seq % NR_BUFS] = new Timer(); //create a new timer for that frame
		normalTimer[seq % NR_BUFS].schedule(new NormalTimerTask(seq), retransmissionTimeout); //after timeout, execute the timer task
	}

	//stop the retransmission timer
	private void stopTimer(int seq)
	{
		//if a timer is running for that frame
		if (normalTimer[seq % NR_BUFS] != null)
			normalTimer[seq % NR_BUFS].cancel(); //cancel the timer
	}

	//start the separate ack frame timer
	private void startAckTimer( )
	{
		stopAckTimer(); //stop the currently running timer
		ackTimer = new Timer(); //create a new timer object
		ackTimer.schedule(new ACKTimerTask(), separateAckTimeout); //after timeout, execute the timer task
	}

	//stop separate ack frame timer
	private void stopAckTimer()
	{
		//if timer is running
		if (ackTimer != null)
			ackTimer.cancel(); //cancel it
	}

	//TimerTask is an abstract class that needs to be implemented
	class NormalTimerTask extends TimerTask
	{
		int seq;

		public NormalTimerTask(int seq)
		{
	        	this.seq = seq; //will be used below
	    	}
		
		//run method is abstract and needs to be implemented
	    	@Override
	    	public void run()
	    	{
			stopTimer(this.seq); //stop retransmission timer for this frame after timeout
			swe.generate_timeout_event(seq); 
	    	}
	}

	//TimerTask is an abstract class that needs to be implemented
	private class ACKTimerTask extends TimerTask
	{
		//implement run
		@Override
		public void run()
		{
			stopAckTimer(); //stop separate ack frame timer for this frame after timeout
	        	swe.generate_acktimeout_event();
		}
	}

	//protocol 6
	public void protocol6()
	{
		oldest_frame = MAX_SEQ + 1; //oldest frame in the window
		int ackExpected = 0; //minimum edge of the sender's window  
		int nextFrameToSend = 0; //maximum edge of the sender's window + 1
		int frameReceiveExpected = 0; //minimum edge of the receiver's window
		int receiveOutOfRange = NR_BUFS; //maximum edge of the receiver's window + 1

		PFrame frame = new PFrame(); //new frame structure	

		Packet inputBuffer[] = new Packet[NR_BUFS]; //the receiving buffer	
		boolean arrived[] = new boolean[NR_BUFS]; //to keep track if a frame has arrived

		init(); //initialise out_buf, see method declared above

		//initialise arrive and inputBuffer arrays
		for (int i = 0; i < NR_BUFS; i++)
		{
			arrived[i] = false;
			inputBuffer[i] = new Packet();
		}

		enable_network_layer(NR_BUFS); //enable network layer

		int numBuffered = 0; //number of buffer slots filled

		while(true)
		{
			wait_for_event(event); //wait for event
			
			//depending on event type
			switch(event.type)
			{
				//network layer ready
				case (PEvent.NETWORK_LAYER_READY):  
					numBuffered++; //a slot in out_buf will be filled   
					from_network_layer(out_buf[nextFrameToSend % NR_BUFS]); //collect data from network layer and place it in out_buf
					sendFrame(PFrame.DATA, nextFrameToSend, frameReceiveExpected, out_buf); //send the frame
					nextFrameToSend = inc(nextFrameToSend); //move on to the next frame to send  
					break;

				//a frame has arrived
				case (PEvent.FRAME_ARRIVAL):   
					from_physical_layer(frame); //collect a frame from the physical layer 
				
					//if data frame
					if (frame.kind == PFrame.DATA)	
					{
						//if frame received is not that expected, and noNak is not used
						if (frame.seq != frameReceiveExpected && noNak)
							sendFrame(PFrame.NAK, 0, frameReceiveExpected, out_buf); //send NAK
						else
							startAckTimer(); //start timer in case there is no frame to piggyback the ack

						//if frame received is within the window, and the receiver's buffer is empty at the assigned slot
						if (between(frameReceiveExpected, frame.seq, receiveOutOfRange) && arrived[frame.seq % NR_BUFS]==false)
						{
							arrived[frame.seq % NR_BUFS] = true;//indicate that the receiver's buffer at that slot is now filled	
							inputBuffer[frame.seq % NR_BUFS] = frame.info; //fill the receiver's buffer with the data
							
							//if a frame expected has arrived, move on to the next frame expected
							//loop till we land on an expected frame that has not yet arrived
							while (arrived[frameReceiveExpected % NR_BUFS])
							{
								//send the data of the frame to the network layer
								to_network_layer(inputBuffer[frameReceiveExpected % NR_BUFS]); 
								noNak = true; //frame is expected, so all is good
								//make the slot available to receive the next frame
								arrived[frameReceiveExpected % NR_BUFS] = false; 
								frameReceiveExpected = inc(frameReceiveExpected); //move on to the next frame expected	
								receiveOutOfRange = inc(receiveOutOfRange);//advance the max edge of the receiver's window
								startAckTimer(); //in case there are no frames to piggyback ack for a long time
							}
						}
					}

					//if NAK frame 
					if (frame.kind == PFrame.NAK && between(ackExpected, (frame.ack + 1) % (MAX_SEQ + 1), nextFrameToSend))
						sendFrame(PFrame.DATA, (frame.ack + 1) % (MAX_SEQ + 1), frameReceiveExpected, out_buf); //resend frame
					while (between(ackExpected, frame.ack, nextFrameToSend))
					{
						numBuffered--;	
						stopTimer(ackExpected % NR_BUFS);	
						ackExpected = inc(ackExpected);	
						enable_network_layer(1);
					}
					break;

				//damaged frame 
				case (PEvent.CKSUM_ERR):
					if (noNak)
						sendFrame(PFrame.NAK, 0, frameReceiveExpected, out_buf); //send back NAK	
					break;

				//if retransmission timeout
				case (PEvent.TIMEOUT):	
					sendFrame(PFrame.DATA, oldest_frame, frameReceiveExpected, out_buf); //resend data frame
					break;

				//if ack timeout
				case (PEvent.ACK_TIMEOUT):	
					sendFrame(PFrame.ACK, 0, frameReceiveExpected, out_buf); //send a separate ack frame
					break;
				default:
					System.out.println("SWP: undefined event type = " + event.type);
					System.out.flush();
			}
		}
	}

}//End of class


/* Note: In class SWE, the following two public methods are available:
   . generate_acktimeout_event() and
   . generate_timeout_event(seqnr).

   To call these two methods (for implementing timers),
   the "swe" object should be referred as follows:
     swe.generate_acktimeout_event(), or
     swe.generate_timeout_event(seqnr).
*/


