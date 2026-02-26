MAS 2025-26 - First Lab Assignment: Programming Agents with JADE
====================================================================
Author: Francisco Manuel Vazquez Fernandez
Master in Artificial Intelligence (USC)

DESCRIPTION
This package contains the source code, compiled classes, and execution 
scripts for the Item World multi-agent simulation. It includes a baseline 
Random Agent, a reactive Greedy Agent (Type A), and a deliberative, 
adaptive Planner Agent (Type B).

PREREQUISITES (WSL / Linux)
Ensure you have Java Development Kit (JDK) installed and the JADE library 
available. You must export the JADE classpath before compiling or running.

    export CP=".:/home/yourComputer/jade/jade/lib/jade.jar"

COMPILATION
To compile all agents manually from the source code, run:

    javac -cp $CP *.java

EXECUTION: COMMAND LINE INSTANTIATION
As requested in the assignment guidelines, you can instantiate the agents 
directly from the command line. The agents accept a single integer 
argument defining their "commitment" parameter.

Example (Running the Simulator with all 3 agents at commitment 1):

    java -cp $CP jade.Boot -gui -agents "sim:SimulatorAgent;player1:RandomAgent(1);player2:GreedyAgent(1);player3:PlannerAgent(1)"
