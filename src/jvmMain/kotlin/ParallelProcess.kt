/**Performs operations on input data concurrently.
 *
 * @author Adam Howard
 * @since 17/09/2018
 */
internal class ParallelProcess<In, Out>(private val workFunction: (input: In) -> Out) {
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

    /**Get the resulting output from the worker threads.
     * This is a blocking method; it only returns once all worker threads have finished.*/
    fun collectOutputWhenFinished(): List<Out> {
        val outputInProgress = mutableSetOf<Out?>()//nullable because the worker may fail to produce output
        for(thread in workerThreads) {
            thread.join()
            outputInProgress.add(thread.getOutput())
        }
        //get rid of null elements, convert from MutableSet<Out?> to List<Out>
        return outputInProgress.filterNotNull().toList()
    }
}