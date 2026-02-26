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
    private int commitment = 1; 

    @Override
    protected void setup() {
        System.out.println("Hello! RandomAgent " + getAID().getLocalName() + " is ready.");

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                commitment = Integer.parseInt((String) args[0]);
                System.out.println("Commitment set to: " + commitment);
            } catch (NumberFormatException e) {
                System.out.println("Invalid commitment argument. Using default: 1");
            }
        }

        simulatorAID = searchForSimulator();
        
        if (simulatorAID == null) {
            System.out.println("Simulator not found! Terminating.");
            doDelete();
            return;
        }

        registerInSimulation();

        addBehaviour(new GameLoop());
    }

    private AID searchForSimulator() {
        DFAgentDescription template = new DFAgentDescription();
        ServiceDescription sd = new ServiceDescription();
        sd.setType("SimulatorService"); 
        template.addServices(sd);

        try {
            for (int i = 0; i < 5; i++) {
                DFAgentDescription[] result = DFService.search(this, template);
                if (result.length > 0) {
                    return result[0].getName();
                }
                Thread.sleep(1000); 
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

                if ("request-action".equals(convId) && msg.getPerformative() == ACLMessage.REQUEST) {
                    
                    Position nextMove = pickRandomMove();

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

                else if ("update-state".equals(convId) && msg.getPerformative() == ACLMessage.INFORM) {
                    try {
                        SimulationState updatedState = (SimulationState) msg.getContentObject();
                        myState = updatedState; 
                    } catch (UnreadableException e) {
                        e.printStackTrace();
                    }
                }
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
        Position current = myState.getPosition();
        int x = current.x;
        int y = current.y;

        int direction = new Random().nextInt(4);

        switch (direction) {
            case 0: y--; break; 
            case 1: x++; break; 
            case 2: y++; break; 
            case 3: x--; break; 
        }
        
        return new Position(x, y);
    }
}