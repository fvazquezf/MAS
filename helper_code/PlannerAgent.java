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
import java.util.*;

public class PlannerAgent extends Agent {

    private SimulationState myState;
    private AID simulatorAID;
    private int commitment = 1;

// --- INTERNAL STATE FOR DELIBERATIVE AGENT ---
    private List<Position> currentPlan = new ArrayList<>();
    private Position currentTarget = null;
    private MapNavigator navigator = new MapNavigator(); 

    // --- ADAPTIVE LEARNING VARIABLES ---
    private int turnsSinceLastUpdate = 0;
    private int stepsTakenSinceUpdate = 0;
    private int learnedMaxSafeSteps = 1000; // Starts incredibly high
    private Position lastRequestedPosition = null;
    private boolean waitingForFreshMap = false;

    @Override
    protected void setup() {
        System.out.println("Hello! PlannerAgent (Type B) " + getAID().getLocalName() + " is ready.");

        Object[] args = getArguments();
        if (args != null && args.length > 0) {
            try {
                commitment = Integer.parseInt((String) args[0]);
                System.out.println(getLocalName() + " Commitment set to: " + commitment);
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
                if (result.length > 0) return result[0].getName();
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
                System.out.println(getLocalName() + " joined simulation! Starting at " + myState.getPosition());
            } catch (UnreadableException e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Could not join simulation.");
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
                    
                    // Call the Deliberative decision function
                    Position nextMove = findDeliberativeMove();

                    ACLMessage reply = msg.createReply();
                    reply.setPerformative(ACLMessage.PROPOSE);
                    try {
                        reply.setContentObject(nextMove);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    myAgent.send(reply);
                }
                else if ("update-state".equals(convId) && msg.getPerformative() == ACLMessage.INFORM) {
                    try {
                        myState = (SimulationState) msg.getContentObject();
                    } catch (UnreadableException e) {
                        e.printStackTrace();
                    }
                }
                else if ("simulation-complete".equals(convId) && msg.getPerformative() == ACLMessage.INFORM) {
                    System.out.println(myAgent.getLocalName() + ": Simulation finished. Terminating.");
                    myAgent.doDelete();
                }
            } else {
                block();
            }
        }
    }

    private Position findDeliberativeMove() {
        Map currentMap = myState.getMap();
        Position currentPos = myState.getPosition();

        turnsSinceLastUpdate++;
        if (turnsSinceLastUpdate >= commitment) {
            turnsSinceLastUpdate = 0;
            stepsTakenSinceUpdate = 0;
            waitingForFreshMap = false; 
        }

        if (lastRequestedPosition != null && !currentPos.equals(lastRequestedPosition) && !waitingForFreshMap) {
            learnedMaxSafeSteps = Math.max(1, stepsTakenSinceUpdate);
            
            System.out.println(getLocalName() + " bumped into something! Updating learnedMaxSafeSteps to: " + learnedMaxSafeSteps);
            
            waitingForFreshMap = true; 
        }
        if (waitingForFreshMap || stepsTakenSinceUpdate >= learnedMaxSafeSteps) {
            lastRequestedPosition = currentPos; 
            return currentPos; 
        }

        if (needsReplanning(currentMap, currentPos)) {
            currentPlan = calculatePathBFS(currentMap, currentPos);
            if (!currentPlan.isEmpty()) {
                currentTarget = currentPlan.get(currentPlan.size() - 1);
            } else {
                currentTarget = null;
            }
        }

        if (currentPlan != null && !currentPlan.isEmpty()) {
            Position nextStep = currentPlan.remove(0);
            lastRequestedPosition = nextStep;
            stepsTakenSinceUpdate++;     
            return nextStep;
        }

        lastRequestedPosition = currentPos;
        return currentPos;
    }

    private boolean needsReplanning(Map map, Position currentPos) {
        if (currentPlan == null || currentPlan.isEmpty() || currentTarget == null) return true;
        if (!map.isItemPosition(currentTarget)) return true;
        
        for (Position p : currentPlan) {
            if (map.isTrapPosition(p)) return true;
        }
        return false;
    }

    private List<Position> calculatePathBFS(Map map, Position start) {
        Queue<List<Position>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>();

        List<Position> startPath = new ArrayList<>();
        queue.add(startPath);
        visited.add(start.x + "," + start.y);

        while (!queue.isEmpty()) {
            List<Position> path = queue.poll();
            Position current = path.isEmpty() ? start : path.get(path.size() - 1);

            if (map.isItemPosition(current)) {
                return path; 
            }

            List<Position> neighbors = navigator.getNextPossiblePositions(map, current);

            for (Position neighbor : neighbors) {
                String posKey = neighbor.x + "," + neighbor.y;
                
                if (!visited.contains(posKey) && !map.isTrapPosition(neighbor)) {
                    visited.add(posKey);
                    
                    List<Position> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(newPath);
                }
            }
        }

        return new ArrayList<>(); 
    }

    private Position getRandomSafeStep(Map map, Position current) {
        List<Position> neighbors = navigator.getNextPossiblePositions(map, current);
        List<Position> safeMoves = new ArrayList<>();
        
        for (Position p : neighbors) {
            if (!map.isTrapPosition(p)) safeMoves.add(p);
        }
        
        if (safeMoves.isEmpty()) return current; 
        return safeMoves.get(new Random().nextInt(safeMoves.size()));
    }
}