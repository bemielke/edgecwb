/*
 * This software is in the public domain because it contains materials 
 * that originally came from the United States Geological Survey, 
 * an agency of the United States Department of Interior. For more 
 * information, see the official USGS copyright policy at 
 * http://www.usgs.gov/visual-id/credit_usgs.html#copyright
 */


package gov.usgs.anss.ewexport;
import gov.usgs.anss.edgeoutput.TraceBuf;
import gov.usgs.anss.edgethread.EdgeThread;
import java.util.ArrayList;
/** This class was written as a queue between a EWExportSocket and and EWExportOutputer.
 *
 * It contains a fixed size set of TraceBufs.  TraceBufs are preallocated on the queue
 * and "queuing" consists of copying the tracebufs to one of the internal elements.  in this
 * way the queuer is free to reuse the input tracebuf.  The dequeue is a two step process
 * 1)  the actual object on the queue is returned with getTail(), when processing of this
 * has been completed, the user calls bumpTail() to indicate the space can now be reused.
 * All of this was done to make it so TraceBufs would not be created and destroyed all
 * of the time, but reused in place.
 *
 * @author U.S. Geological Survey  <ketchum at usgs.gov>
 */
public class TraceBufQueue {
  private int queueSize;
  private final ArrayList<TraceBuf> queue;
  private int [] seqs;
  private int head;       // the  one added to memory buffer
  private int tail;       // The last one processed, so next to process is +1
  private boolean outstanding;
  private final EdgeThread par;
  private boolean dbg;
  private final String tag;
  private int lastSeq=-1;
  private int nqueue;
  private int ndqueue;
  private int maxUsed=0;
  public void setDebug(boolean b) {dbg=b;}
  public void resetMaxused() {maxUsed=0;}
  public int getMaxUsed() {return maxUsed;}
  public int used() {return (head-tail >= 0? head-tail:head-tail+queueSize);}
  @Override
  public String toString() {return "TBQueue size="+queueSize+" head="+head+" tail="+tail+
          " used="+(head-tail >= 0? head-tail:head-tail+queueSize)+" maxused="+maxUsed+" outstanding="+outstanding;}
  /** return size of the queue
   *
   * @return  the queue size
   */
  public int getSize() {return queueSize;}
  /** create a TraceBuf queue for given size
   *
   * @param size Number of elements in queue
   * @param tg logging tag
   * @param parent Logging EdgeThread
   */
  public TraceBufQueue(int size, String tg, EdgeThread parent) {
    par = parent;
    tag=tg;
    queue = new ArrayList<>(size);
    seqs = new int[size];
    for(int i=0; i<size; i++) {seqs[i]=-1;queue.add(new TraceBuf(TraceBuf.TRACE_MAX_NSAMPS));}
    queueSize=size;
    head=size-1;
    tail=size-1;
  }
  /** add a TraceBuf, return false if queue is full
   *
   * @param tb The TraceBuf to add
   * @param sq sequence number
   * @return false, if the queue is full, true if add is successful.
   */
 
  public synchronized boolean add(TraceBuf tb, int sq) {
    if(hasRoom()) {
      int next = (head+1) % queueSize;
      queue.get(next).load(tb.getBuf());
      seqs[next] = sq;
      head=next;
      if(dbg) par.prta(tag+"  q="+toString()+" sq="+sq+" "+tb.toString().substring(0,100));
      nqueue++;
      if(lastSeq == -1) lastSeq = sq-1;
      maxUsed = Math.max(maxUsed, (head-tail >= 0? head-tail:head-tail+queueSize));
      
      return true;
    }
    else return false;
  }
  /** Check on space in the queue
   *
   * @return false, if the queue is full, true if add is successful.
   */
  public synchronized boolean hasRoom() {
     int next = (head+1) % queueSize;
     return next != tail;
  }
  /** Get the tail element, user must call bumpTail() when process int of this object is comleted.
   *
   * @return The TraceBuf of the tail or null if the queue is empty.
   */
  public synchronized TraceBuf getTail() {
    if(head == tail)  return null;  // queue is empty
    outstanding=true;
    return queue.get((tail+1) % queueSize);
  }
  public synchronized int getTailSeq() {return seqs[(tail+1) % queueSize];}
  /** indicate the tail element is processed and this element should be freed on the queue
   *
   */
  public synchronized void bumpTail() {
    if(!outstanding) throw new IndexOutOfBoundsException("Attempt to bumpTail when no element has been obtained with getTail()");
    outstanding=false;
    tail = (tail+1) % queueSize;
    if(dbg) par.prta(tag+" dq="+toString()+" sq="+seqs[tail]+" "+ queue.get(tail).toString());
    ndqueue++;
    // This would only be a valid check if all data in ring buffer goes to this receiver!
    //if(seqs[tail] != lastSeq+1) par.prta(tag+" **** out of seq should be "+(lastSeq+1)+" is "+seqs[tail]);
    lastSeq = seqs[tail];
  }
  /** Resize the queue to bigger size
   *
   * @param newSize The new size of the queue in elements.
   */
  public synchronized void resizeQueue(int newSize) {
    if(newSize <= queueSize) return;      // user is trying to make smaller
    for(int i=queueSize; i<newSize; i++) queue.add(i, new TraceBuf(TraceBuf.TRACE_MAX_NSAMPS));  // Add on to the array list the number of elements.
    int [] tmp = new int[newSize];

    // Now we need to play with tail and head to make the queue right.
    if(tail <= head) {queueSize=newSize; return;}      // All of the elements are in order at the bottom of the queuue
    // The tail elements need to be moved to the end of the queue and tail adjusted.
    for(int i=tail; i<queueSize; i++) {
      tmp[newSize - (queueSize - tail)] = seqs[i];
      queue.get(newSize - (queueSize - tail)).load(queue.get(i).getBuf());
    }
    seqs = tmp;
    queueSize = newSize;
  }
  /** try to move the tail back so many places, if not successful, leave it alone
   * 
   * @param places
   * @return 
   */
  public synchronized boolean  moveTailBack(int places) {
    int newtail = tail;
    for(int i=0; i<places; i++) {
      newtail--;
      if(newtail < 0) newtail += queueSize;
      if(newtail == ((head+1) % queueSize)) {   // did this just hit head+1
        par.prt("Attempt to move tail too far back.  Abort operation head="+head+" tail="+tail+" places="+places);
        return false;
      }
    }
    tail = newtail;
    return true;
  }
}
