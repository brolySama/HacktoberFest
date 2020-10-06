package negotiation.flows

import co.paralleluniverse.fibers.Suspendable
import negotiation.contracts.DemoContract
import negotiation.states.DemoState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.ProgressTracker

object DemoFlow{
    @InitiatingFlow
    @StartableByRPC

    class Initiator(val id:String,val amount:Int,val counterParty: Party): FlowLogic<UniqueIdentifier>() {
        override val progressTracker: ProgressTracker?
            get() = super.progressTracker


        override fun call():UniqueIdentifier {
            // Creating the output.
            val output=DemoState(id,amount,ourIdentity,counterParty)
            //creating commands
            val commandType=DemoContract.DemoCommands.onBoard()
            val reqSigners= listOf(ourIdentity.owningKey,counterParty.owningKey)
            val command=Command(commandType,reqSigners)
            // Building the transaction.
            val notary = serviceHub.networkMapCache.notaryIdentities.first()//initialize the notary
            val txBuilder = TransactionBuilder(notary)//starting the transaction needs a notary first
            txBuilder.addOutputState(output, DemoContract.id)/** Adds an output state. A default notary must be specified during builder construction to use this method */
            txBuilder.addCommand(command)//adding the commands

            // Signing the transaction ourselves.
            val partStx = serviceHub.signInitialTransaction(txBuilder)

            // Gathering the counterparty's signature.
            val counterpartySession = initiateFlow(counterParty)
            val fullyStx = subFlow(CollectSignaturesFlow(partStx, listOf(counterpartySession)))

            // Finalising the transaction.
            val finalisedTx = subFlow(FinalityFlow(fullyStx, listOf(counterpartySession)))
            return finalisedTx.tx.outputsOfType<DemoState>().single().linearId
        }

    }

    @InitiatedBy(DemoFlow.Initiator::class)
    class Responder(val counterpartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() {
            val signTransactionFlow = object : SignTransactionFlow(counterpartySession) {
                override fun checkTransaction(stx: SignedTransaction) {
                    // No checking to be done.
                }
            }

            val txId = subFlow(signTransactionFlow).id

            subFlow(ReceiveFinalityFlow(counterpartySession, txId))
        }
    }
}