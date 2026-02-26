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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class GreedyAgent extends Agent {

    private SimulationState myState;
    private AID simulatorAID;
    private int commitment = 1;

    @Override
    protected void setup() {
        System.out.println("Hello! GreedyAgent (Type A) " + getAID().getLocalName() + " is ready.");

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
                    
                    Position nextMove = findGreedyMove();

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

    private Position findGreedyMove() {
        Map currentMap = myState.getMap();
        Position currentPos = myState.getPosition();

        List<Position> safeMoves = getSafeAdjacentMoves(currentMap, currentPos);

        if (safeMoves.isEmpty()) {
            return currentPos; 
        }

        LinkedList<Position> items = currentMap.getItemPositions();

        if (items.isEmpty()) {
            return safeMoves.get(new Random().nextInt(safeMoves.size()));
        }

        Position closestItem = getClosestTarget(currentPos, items);

        return getBestMoveTowardsTarget(safeMoves, closestItem);
    }

    private List<Position> getSafeAdjacentMoves(Map map, Position current) {
        List<Position> moves = new ArrayList<>();
        Position[] possibleMoves = {
            new Position(current.x - 1, current.y), 
            new Position(current.x + 1, current.y), 
            new Position(current.x, current.y - 1),
            new Position(current.x, current.y + 1)
        };

        for (Position move : possibleMoves) {
            if (map.withinMapLimits(move) && !map.isTrapPosition(move)) {
                moves.add(move);
            }
        }
        return moves;
    }

    private Position getClosestTarget(Position start, List<Position> targets) {
        Position closest = null;
        int minDistance = Integer.MAX_VALUE;

        for (Position target : targets) {
            int dist = getManhattanDistance(start, target);
            if (dist < minDistance) {
                minDistance = dist;
                closest = target;
            }
        }
        return closest;
    }

    private Position getBestMoveTowardsTarget(List<Position> safeMoves, Position target) {
        Position bestMove = null;
        int minDistance = Integer.MAX_VALUE;

        for (Position move : safeMoves) {
            int dist = getManhattanDistance(move, target);
            if (dist < minDistance) {
                minDistance = dist;
                bestMove = move;
            }
        }
        return bestMove;
    }

    private int getManhattanDistance(Position p1, Position p2) {
        return Math.abs(p1.x - p2.x) + Math.abs(p1.y - p2.y);
    }
}