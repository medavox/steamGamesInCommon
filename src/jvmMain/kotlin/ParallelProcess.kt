/**Performs operations on input data concurrently.
 *
 * @author Adam Howard
 * @since 17/09/2018
 */
internal class ParallelProcess<In, Out>(private val workFunction:(input:In) -> Out) {
    private val workerThreads:MutableSet<InternalWorker> = mutableSetOf()

    /**Run this instance's worker repeatedly concurrently with the same input.
     * @param numberOfThreads the number of threads --
     * and the number of times to repeat -- running the worker
     * @param input the input to run on*/
    fun repeatOnInput(numberOfThreads:Int, input:In) {
        for(i in 1..numberOfThreads) {
            val worker = InternalWorker(input, workFunction)
            workerThreads.add(worker)
            worker.start()
        }
    }

    /**Process an array in parallel, with one worker thread per array element.
     * @param input the array to iterate over concurrently.*/
    fun oneWorkerPerElement(input:Array<out In>) {
        for(i in input.indices) {
            val worker = InternalWorker(input[i], workFunction)
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

    private inner class InternalWorker(val input: In, val workFunction:(input:In) -> Out): Thread() {
        private var out: Out? = null

        override fun run() {
            super.run()
            out = workFunction(input)
        }

        fun getOutput(): Out? {
            return out
        }
    }
}