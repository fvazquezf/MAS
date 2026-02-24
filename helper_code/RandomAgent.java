import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.CyclicBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.lang.acl.UnreadableException;

import java.io.IOException;
import java.util.Random;

public class RandomAgent extends Agent {

    private SimulationState myState;
    private AID simulatorAID;
    private int commitment = 1; // Default commitment

    @Override
    protected void setup() {
        System.out.println("Hello! RandomAgent " + getAID().getLocalName() + " is ready.");

        // 1. Get arguments (commitment)
        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                commitment = Integer.parseInt((String) args[0]);
                System.out.println("Commitment set to: " + commitment);
            } catch (NumberFormatException e) {
                System.out.println("Invalid commitment argument. Using default: 1");
            }
        }

        // 2. Search for the Simulator in the Yellow Pages (DF)
        simulatorAID = searchForSimulator();
        
        if (simulatorAID == null) {
            System.out.println("Simulator not found! Terminating.");
            doDelete();
            return;
        }

        // 3. Register in the Simulation
        registerInSimulation();

        // 4. Add the Game Loop Behaviour
        addBehaviour(new GameLoop());
    }

    private AID searchForSimulator() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("SimulatorService"); // Must match SimulatorAgent.java
        template.addServices(sd);

        try {
            // Retry a few times if simulator isn't ready yet
            for (int i = 0; i < 5; i++) {
                DFAgentDescription[] result = DFService.search(this, template);
                if (result.length > 0) {
                    return result[0].getName();
                }
                Thread.sleep(1000); // Wait 1 second before retrying
            }
        } catch (FIPAException | InterruptedException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void registerInSimulation() {
        ACLMessage msg = new ACLMessage(ACLMessage.REQUEST);
        msg.addReceiver(simulatorAID);
        msg.setConversationId("join-simulation-request");
        msg.setContent(String.valueOf(commitment));
        send(msg);

        // Wait for the AGREE response containing the initial state
        MessageTemplate mt = MessageTemplate.MatchConversationId("join-simulation-request");
        ACLMessage reply = blockingReceive(mt);

        if (reply != null && reply.getPerformative() == ACLMessage.AGREE) {
            try {
                myState = (SimulationState) reply.getContentObject();
                System.out.println("Joined simulation! Starting at " + myState.getPosition());
            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Could not join simulation. (Did you wait too long?)");
            doDelete();
        }
    }

    private class GameLoop extends CyclicBehaviour {
        @Override
        public void action() {
            ACLMessage msg = myAgent.receive();

            if (msg != null) {
                String convId = msg.getConversationId();

                // --- CASE 1: REQUEST FOR ACTION (IT'S MY TURN) ---
                if ("request-action".equals(convId) && msg.getPerformative() == ACLMessage.REQUEST) {
                    
                    // 1. Decide a random move
                    Position nextMove = pickRandomMove();

                    // 2. Send PROPOSE
                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.PROPOSE);
                    try {
                        reply.setContentObject(nextMove);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    myAgent.send(reply);
                    System.out.println(myAgent.getLocalName() + " moved to " + nextMove);
                }

                // --- CASE 2: STATE UPDATE (MOVE RESULT) ---
                else if ("update-state".equals(convId) && msg.getPerformative() == ACLMessage.INFORM) {
                    try {
                        SimulationState updatedState = (SimulationState) msg.getContentObject();
                        // Update our internal state (essential for Smart agents, optional for Random)
                        myState = updatedState; 
                    } catch (UnreadableException e) {
                        e.printStackTrace();
                    }
                }

                // --- CASE 3: GAME OVER ---
                else if ("simulation-complete".equals(convId) && msg.getPerformative() == ACLMessage.INFORM) {
                    System.out.println("Simulation finished. Terminating.");
                    myAgent.doDelete();
                }
            } else {
                block();
            }
        }
    }

    private Position pickRandomMove() {
        // Get current position
        Position current = myState.getPosition();
        int x = current.x;
        int y = current.y;

        // 0=North, 1=East, 2=South, 3=West
        int direction = new Random().nextInt(4);

        switch (direction) {
            case 0: y--; break; // North (Up)
            case 1: x++; break; // East (Right)
            case 2: y++; break; // South (Down)
            case 3: x--; break; // West (Left)
        }
        
        // Note: We don't check map boundaries here because the Random Agent is "dumb".
        // The SimulatorAgent.java logic handles invalid moves by keeping us in the same spot.
        return new Position(x, y);
    }
}