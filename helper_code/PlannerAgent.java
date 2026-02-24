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

    // --- DELIBERATIVE DECISION LOGIC ---

    private Position findDeliberativeMove() {
        Map currentMap = myState.getMap();
        Position currentPos = myState.getPosition();

        // 1. Evaluate if our current plan is broken or finished
        if (needsReplanning(currentMap, currentPos)) {
            System.out.println(getLocalName() + " is deliberating a new plan...");
            currentPlan = calculatePathBFS(currentMap, currentPos);
            
            if (!currentPlan.isEmpty()) {
                currentTarget = currentPlan.get(currentPlan.size() - 1);
            } else {
                currentTarget = null;
            }
        }

        // 2. Execute the next step of the plan
        if (currentPlan != null && !currentPlan.isEmpty()) {
            return currentPlan.remove(0); // Pop the next move off the list
        }

        // 3. Fallback: If no plan can be made (e.g., trapped or no items), try to move randomly but safely
        return getRandomSafeStep(currentMap, currentPos);
    }

    private boolean needsReplanning(Map map, Position currentPos) {
        // No plan exists
        if (currentPlan == null || currentPlan.isEmpty() || currentTarget == null) return true;

        // The target item we were walking towards is no longer there!
        if (!map.isItemPosition(currentTarget)) return true;

        // A trap suddenly appeared somewhere along our planned path!
        for (Position p : currentPlan) {
            if (map.isTrapPosition(p)) return true;
        }

        // We were bumped or rejected on the last turn, so we aren't where the plan expected us to be
        Position nextExpectedStep = currentPlan.get(0);
        int dist = Math.abs(nextExpectedStep.x - currentPos.x) + Math.abs(nextExpectedStep.y - currentPos.y);
        if (dist != 1) return true;

        return false; // Plan is solid!
    }

    private List<Position> calculatePathBFS(Map map, Position start) {
        // Breadth-First Search ensures we find the shortest path
        Queue<List<Position>> queue = new LinkedList<>();
        Set<String> visited = new HashSet<>(); // Using Strings "x,y" because Position might not have a reliable hashCode()

        List<Position> startPath = new ArrayList<>();
        queue.add(startPath);
        visited.add(start.x + "," + start.y);

        while (!queue.isEmpty()) {
            List<Position> path = queue.poll();
            Position current = path.isEmpty() ? start : path.get(path.size() - 1);

            // If we reached an item, we found our optimal path!
            if (map.isItemPosition(current)) {
                return path; 
            }

            // Use the professor's MapNavigator to get adjacent nodes
            List<Position> neighbors = navigator.getNextPossiblePositions(map, current);

            for (Position neighbor : neighbors) {
                String posKey = neighbor.x + "," + neighbor.y;
                
                // Only explore if it's unvisited and NOT a trap
                if (!visited.contains(posKey) && !map.isTrapPosition(neighbor)) {
                    visited.add(posKey);
                    
                    List<Position> newPath = new ArrayList<>(path);
                    newPath.add(neighbor);
                    queue.add(newPath);
                }
            }
        }

        return new ArrayList<>(); // Returns empty plan if no items are reachable
    }

    private Position getRandomSafeStep(Map map, Position current) {
        List<Position> neighbors = navigator.getNextPossiblePositions(map, current);
        List<Position> safeMoves = new ArrayList<>();
        
        for (Position p : neighbors) {
            if (!map.isTrapPosition(p)) safeMoves.add(p);
        }
        
        if (safeMoves.isEmpty()) return current; // Completely trapped
        return safeMoves.get(new Random().nextInt(safeMoves.size()));
    }
}