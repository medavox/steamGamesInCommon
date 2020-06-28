import java.util.Queue

/**Performs operations on input data concurrently.
 *
 * @author Adam Howard
 * @since 17/09/2018
 */
internal class ParallelProcess<In, Out>(private val workFunction: (input: In) -> Out) {
    var finishWhenQueueIsEmpty:Boolean = false
    var threadsKillswitch:Boolean = false
    private val workerThreads:MutableSet<Thread> = mutableSetOf()
    lateinit var outputInProgress:MutableList<Out?>//nullable because worker threads may fail to produce output
    /**Run this instance's worker repeatedly concurrently with the same input.
     * @param numberOfWorkerThreads the number of threads --
     * and the number of times to repeat -- running the worker
     * @param input the input to run on*/
    fun repeatOnInput(input: In, numberOfWorkerThreads: Int = 5) {
        outputInProgress = MutableList(numberOfWorkerThreads) {null}
        for(i in 0 until numberOfWorkerThreads) {
            val worker = Thread { outputInProgress.add(workFunction(input)) }
            workerThreads.add(worker)
            worker.start()
        }
    }

    /**Process an array in parallel, with one worker thread per array element.
     * @param input the array to iterate over concurrently.*/
    fun oneWorkerPerElement(input: Array<out In>) {
        outputInProgress = MutableList(input.size) {null}
        for(i in input.indices) {
            val worker = Thread { outputInProgress.add(workFunction(input[i])) }
            workerThreads.add(worker)
            worker.start()
        }
    }
    /**Process elements in a queue (that may still be added to at any time) using a pool of worker threads.
     * The number of worker threads is usually far fewer than the number of elements in the queue at any one time.
     * In other words: performs work parallely, but not TOO parallely
     * @param inputQueue the queue of elements to be processed
     * @param numberOfWorkerThreads number of parallel worker threads. Defaults to 5*/
    fun processMutableQueueWithWorkerPool(inputQueue: Queue<out In>, numberOfWorkerThreads: Int = 5, waitTime:Long) {
        outputInProgress = mutableListOf()//the output list is an as-yet unknown size,
        //so just initialise it and let the workers add their elements.
        //let's hope Kotlin Lists are thread-safe!
        for(i in 1..numberOfWorkerThreads) {
            val worker = Thread {
                while (!threadsKillswitch) {
                    //spins until there is something in the queue
                    if (!inputQueue.isEmpty()) {
                        val next: In = inputQueue.remove()
                        outputInProgress.add(workFunction(next))
                        if (finishWhenQueueIsEmpty) { //while we're still working when we've been told to stop when the queue is empty,
                            // indicate the number of items left
                            println("items left in queue:" + inputQueue.size)
                        }
                    } else if (finishWhenQueueIsEmpty) { //this thread's been told to exit when the download queue is empty
                        break
                    } else {  //wait before checking again, so as not to waste CPU cycles
                        try {
                            Thread.sleep(waitTime)
                        } catch (ie: InterruptedException) {
                            System.err.println("Can't a thread catch a few ms sleep around here?!")
                            ie.printStackTrace()
                        }
                    }
                }
            }
            workerThreads.add(worker)
            worker.start()
        }
    }

    /**Get the resulting output from the worker threads.
     * This is a blocking method; it only returns once all worker threads have finished.*/
    fun collectOutputWhenFinished(): List<Out> {
        for(thread in workerThreads) {
            thread.join()
        }
        //get rid of null elements, convert from MutableSet<Out?> to List<Out>
        return outputInProgress.filterNotNull().toList()
    }
}