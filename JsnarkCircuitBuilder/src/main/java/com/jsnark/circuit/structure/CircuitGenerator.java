/*******************************************************************************
 * Author: Ahmed Kosba <akosba@cs.umd.edu>
 *******************************************************************************/
package com.jsnark.circuit.structure;

import com.jsnark.circuit.auxiliary.LongElement;
import com.jsnark.circuit.config.Config;
import com.jsnark.circuit.eval.CircuitEvaluator;
import com.jsnark.circuit.eval.Instruction;
import com.jsnark.circuit.operations.WireLabelInstruction;
import com.jsnark.circuit.operations.WireLabelInstruction.LabelType;
import com.jsnark.circuit.operations.primitive.AssertBasicOp;
import com.jsnark.circuit.operations.primitive.BasicOp;
import com.jsnark.circuit.operations.primitive.MulBasicOp;

import java.io.*;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.concurrent.ConcurrentHashMap;

public abstract class CircuitGenerator {

	private static ConcurrentHashMap<Long, CircuitGenerator> activeCircuitGenerators = new ConcurrentHashMap<>();
	private static CircuitGenerator instance;

	protected int currentWireId;
	protected LinkedHashMap<Instruction, Instruction> evaluationQueue;

	protected Wire zeroWire;
	protected Wire oneWire;

	protected ArrayList<Wire> inWires;
	protected ArrayList<Wire> outWires;
	protected ArrayList<Wire> proverWitnessWires;

	protected String circuitName;

	protected HashMap<BigInteger, Wire> knownConstantWires;

	private int numOfConstraints;
	private CircuitEvaluator circuitEvaluator;

	public CircuitGenerator(String circuitName) {

		this.circuitName = circuitName;

		instance = this;
		inWires = new ArrayList<Wire>();
		outWires = new ArrayList<Wire>();
		proverWitnessWires = new ArrayList<Wire>();
		evaluationQueue = new LinkedHashMap<Instruction, Instruction>();
		knownConstantWires = new HashMap<BigInteger, Wire>();
		currentWireId = 0;
		numOfConstraints = 0;

		if (Config.runningMultiGenerators) {
			activeCircuitGenerators.put(Thread.currentThread().getId(), this);
		}
	}

	public static CircuitGenerator getActiveCircuitGenerator() {
		if (!Config.runningMultiGenerators)
			return instance;
		else {

			Long threadId = Thread.currentThread().getId();
			CircuitGenerator currentGenerator = activeCircuitGenerators.get(threadId);
			if (currentGenerator == null) {
				throw new RuntimeException("The current thread does not have any active circuit generators");
			} else {
				return currentGenerator;
			}
		}
	}

	protected abstract void buildCircuit();

	public final void generateCircuit() {
		
		System.out.println("Running Circuit Generator for < " + circuitName + " >");

		initCircuitConstruction();
		buildCircuit();
		
		System.out.println("Circuit Generation Done for < " + circuitName + " >  \n \t Total Number of Constraints :  " + getNumOfConstraints() + "\n");
	}

	public String getName() {
		return circuitName;
	}

	public abstract void generateSampleInput(CircuitEvaluator evaluator);

	public Wire createInputWire(String... desc) {
		Wire newInputWire = new VariableWire(currentWireId++);
		addToEvaluationQueue(new WireLabelInstruction(LabelType.input, newInputWire, desc));
		inWires.add(newInputWire);
		return newInputWire;
	}

	public Wire[] createInputWireArray(int n, String... desc) {
		Wire[] list = new Wire[n];
		for (int i = 0; i < n; i++) {
			if (desc.length == 0) {
				list[i] = createInputWire("");
			} else {
				list[i] = createInputWire(desc[0] + " " + i);
			}
		}
		return list;
	}

	public LongElement createLongElementInput(int totalBitwidth,  String... desc){
		int numWires = (int) Math.ceil(totalBitwidth*1.0/LongElement.CHUNK_BITWIDTH);
		Wire[] w = createInputWireArray(numWires, desc);
		int[] bitwidths = new int[numWires];
		Arrays.fill(bitwidths, LongElement.CHUNK_BITWIDTH);
		if (numWires * LongElement.CHUNK_BITWIDTH != totalBitwidth) {
			bitwidths[numWires - 1] = totalBitwidth % LongElement.CHUNK_BITWIDTH;
		}
		return new LongElement(w, bitwidths);	
	}
	
	public LongElement createLongElementProverWitness(int totalBitwidth, String... desc){
		int numWires = (int) Math.ceil(totalBitwidth*1.0/LongElement.CHUNK_BITWIDTH);
		Wire[] w = createProverWitnessWireArray(numWires, desc);
		int[] bitwidths = new int[numWires];
		Arrays.fill(bitwidths, LongElement.CHUNK_BITWIDTH);
		if (numWires * LongElement.CHUNK_BITWIDTH != totalBitwidth) {
			bitwidths[numWires - 1] = totalBitwidth % LongElement.CHUNK_BITWIDTH;
		}
		return new LongElement(w, bitwidths);	
	}
	
	public Wire createProverWitnessWire(String... desc) {

		Wire wire = new VariableWire(currentWireId++);
		addToEvaluationQueue(new WireLabelInstruction(LabelType.nizkinput, wire, desc));
		proverWitnessWires.add(wire);
		return wire;
	}

	public Wire[] createProverWitnessWireArray(int n, String... desc) {

		Wire[] ws = new Wire[n];
		for (int k = 0; k < n; k++) {
			if (desc.length == 0) {
				ws[k] = createProverWitnessWire("");
			} else {
				ws[k] = createProverWitnessWire(desc[0] + " " + k);
			}
		}
		return ws;
	}

	public Wire[] generateZeroWireArray(int n) {
		Wire[] zeroWires = new ConstantWire[n];
		Arrays.fill(zeroWires, zeroWire);
		return zeroWires;
	}

	public Wire[] generateOneWireArray(int n) {
		Wire[] oneWires = new ConstantWire[n];
		Arrays.fill(oneWires, oneWire);
		return oneWires;
	}

	public Wire makeOutput(Wire wire, String... desc) {
		
		Wire outputWire = wire;
		if(proverWitnessWires.contains(wire)) {
			// The first case is allowed for usability. In some cases, gadgets provide their witness wires as intermediate outputs, e.g., division gadgets,
			// and the programmer could choose any of these intermediate outputs to be circuit outputs later.
			// The drawback of this method is that this will add one constraint for every witness wire that is transformed to be a circuit output.
			// As the statement size is usually small, this will not lead to issues in practice.
			// The constraint is just added for separation. Note: prover witness wires are actually variable wires. The following method is used
			// in order to introduce a different variable.
			outputWire = makeVariable(wire, desc);
			// If this causes overhead, the programmer can create the wires that are causing the bottleneck
			// as input wires instead of prover witness wires and avoid calling makeOutput().
		} else if(inWires.contains(wire)) {
			System.err.println("Warning: An input wire is redeclared as an output. This leads to an additional unnecessary constraint.");
			System.err.println("\t->This situation could happen by calling makeOutput() on input wires or in some cases involving multiplication of an input wire by 1 then declaring the result as an output wire.");
			outputWire = makeVariable(wire, desc);
		} else if (!(wire instanceof VariableWire || wire instanceof VariableBitWire)) {
			wire.packIfNeeded();
			outputWire = makeVariable(wire, desc);
		} else {
			wire.packIfNeeded();
		}

		outWires.add(outputWire);
		addToEvaluationQueue(new WireLabelInstruction(LabelType.output, outputWire, desc));
		return outputWire;

	}

	protected Wire makeVariable(Wire wire, String... desc) {
		Wire outputWire = new VariableWire(currentWireId++);
		Instruction op = new MulBasicOp(wire, oneWire, outputWire, desc);
		Wire[] cachedOutputs = addToEvaluationQueue(op);
		if(cachedOutputs == null){
			return outputWire;
		}
		else{
			currentWireId--;
			return cachedOutputs[0];
		}
	}

	public Wire[] makeOutputArray(Wire[] wires, String... desc) {
		Wire[] outs = new Wire[wires.length];
		for (int i = 0; i < wires.length; i++) {
			if (desc.length == 0) {
				outs[i] = makeOutput(wires[i], "");
			} else {
				outs[i] = makeOutput(wires[i], desc[0] + "[" + i + "]");
			}
		}
		return outs;
	}

	public void addDebugInstruction(Wire w, String... desc) {
		w.packIfNeeded();
		addToEvaluationQueue(new WireLabelInstruction(LabelType.debug, w, desc));
	}

	public void addDebugInstruction(Wire[] wires, String... desc) {
		for (int i = 0; i < wires.length; i++) {
			wires[i].packIfNeeded();
			addToEvaluationQueue(
					new WireLabelInstruction(LabelType.debug, wires[i], desc.length > 0 ? (desc[0] + " - " + i) : ""));
		}
	}

	public void writeCircuitFile() {
		try {
			PrintWriter printWriter = new PrintWriter(new BufferedWriter(new FileWriter(getName() + ".arith")));

			printWriter.println("total " + currentWireId);
			for (Instruction e : evaluationQueue.keySet()) {
				if (e.doneWithinCircuit()) {
					printWriter.print(e + "\n");
				}
			}
			printWriter.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void printCircuit() {

		for (Instruction e : evaluationQueue.keySet()) {
			if (e.doneWithinCircuit()) {
				System.out.println(e);
			}
		}

	}

	private void initCircuitConstruction() {
		oneWire = new ConstantWire(currentWireId++, BigInteger.ONE);
		knownConstantWires.put(BigInteger.ONE, oneWire);
		addToEvaluationQueue(new WireLabelInstruction(LabelType.input, oneWire, "The one-input wire."));
		inWires.add(oneWire);
		zeroWire = oneWire.mul(0);
	}

	public Wire createConstantWire(BigInteger x, String... desc) {
		return oneWire.mul(x, desc);
	}

	public Wire[] createConstantWireArray(BigInteger[] a, String... desc) {
		Wire[] w = new Wire[a.length];
		for (int i = 0; i < a.length; i++) {
			w[i] = createConstantWire(a[i], desc);
		}
		return w;
	}

	public Wire createConstantWire(long x, String... desc) {
		return oneWire.mul(x, desc);
	}

	public Wire[] createConstantWireArray(long[] a, String... desc) {
		Wire[] w = new Wire[a.length];
		for (int i = 0; i < a.length; i++) {
			w[i] = createConstantWire(a[i], desc);
		}
		return w;
	}

	public Wire createNegConstantWire(BigInteger x, String... desc) {
		return oneWire.mul(x.negate(), desc);
	}

	public Wire createNegConstantWire(long x, String... desc) {
		return oneWire.mul(-x, desc);
	}

	/**
	 * Use to support computation for prover witness values outside of the
	 * circuit. See Mod_Gadget and Field_Division gadgets for examples.
	 * 
	 * @param instruction
	 */
	public void specifyProverWitnessComputation(Instruction instruction) {
		addToEvaluationQueue(instruction);
	}

	public final Wire getZeroWire() {
		return zeroWire;
	}

	public final Wire getOneWire() {
		return oneWire;
	}

	public LinkedHashMap<Instruction, Instruction> getEvaluationQueue() {
		return evaluationQueue;
	}

	public int getNumWires() {
		return currentWireId;
	}

	public Wire[] addToEvaluationQueue(Instruction e) {
		if (evaluationQueue.containsKey(e)) {
			if (e instanceof BasicOp) {
				return ((BasicOp) evaluationQueue.get(e)).getOutputs();
			}
		}
		if (e instanceof BasicOp) {
			numOfConstraints += ((BasicOp) e).getNumMulGates();
		}
		evaluationQueue.put(e, e);
		return null;  // returning null means we have not seen this instruction before
	}

	public void printState(String message) {
		System.out.println("\nGenerator State @ " + message);
		System.out.println("\tCurrent Number of Multiplication Gates " + " :: " + numOfConstraints + "\n");
	}

	public int getNumOfConstraints() {
		return numOfConstraints;
	}

	public ArrayList<Wire> getInWires() {
		return inWires;
	}

	public ArrayList<Wire> getOutWires() {
		return outWires;
	}

	public ArrayList<Wire> getProverWitnessWires() {
		return proverWitnessWires;
	}

	/**
	 * Asserts an r1cs constraint. w1*w2 = w3
	 * 
	 */
	public void addAssertion(Wire w1, Wire w2, Wire w3, String... desc) {
		if (w1 instanceof ConstantWire && w2 instanceof ConstantWire && w3 instanceof ConstantWire) {
			BigInteger const1 = ((ConstantWire) w1).getConstant();
			BigInteger const2 = ((ConstantWire) w2).getConstant();
			BigInteger const3 = ((ConstantWire) w3).getConstant();
			if (!const3.equals(const1.multiply(const2).mod(Config.FIELD_PRIME))) {
				throw new RuntimeException("Assertion failed on the provided constant wires .. ");
			}
		} else {
			w1.packIfNeeded();
			w2.packIfNeeded();
			w3.packIfNeeded();
			Instruction op = new AssertBasicOp(w1, w2, w3, desc);
			addToEvaluationQueue(op);
		}
	}

	public void addZeroAssertion(Wire w, String... desc) {
		addAssertion(w, oneWire, zeroWire, desc);
	}

	public void addOneAssertion(Wire w, String... desc) {
		addAssertion(w, oneWire, oneWire, desc);
	}

	public void addBinaryAssertion(Wire w, String... desc) {
		Wire inv = w.invAsBit(desc);
		addAssertion(w, inv, zeroWire, desc);
	}

	public void addEqualityAssertion(Wire w1, Wire w2, String... desc) {
		if(!w1.equals(w2))
			addAssertion(w1, oneWire, w2, desc);
	}

	public void addEqualityAssertion(Wire w1, BigInteger b, String... desc) {
		addAssertion(w1, oneWire, createConstantWire(b, desc), desc);
	}

	public void evalCircuit() {
		circuitEvaluator = new CircuitEvaluator(this);
		generateSampleInput(circuitEvaluator);
		circuitEvaluator.evaluate();
	}

	public void prepFiles() {
		writeCircuitFile();
		if (circuitEvaluator == null) {
			throw new NullPointerException("evalCircuit() must be called before prepFiles()");
		}
		circuitEvaluator.writeInputFile();
	}

	public void runLibsnark() {

		try {
			Process p;
			p = Runtime.getRuntime()
					.exec(new String[] { Config.LIBSNARK_EXEC, circuitName + ".arith", circuitName + ".in" });
			p.waitFor();
			System.out.println(
					"\n-----------------------------------RUNNING LIBSNARK -----------------------------------------");
			String line;
			BufferedReader input = new BufferedReader(new InputStreamReader(p.getInputStream()));
			StringBuffer buf = new StringBuffer();
			while ((line = input.readLine()) != null) {
				buf.append(line + "\n");
			}
			input.close();
			System.out.println(buf.toString());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public CircuitEvaluator getCircuitEvaluator() {
		if (circuitEvaluator == null) {
			throw new NullPointerException("evalCircuit() must be called before getCircuitEvaluator()");
		}
		return circuitEvaluator;
	}

}
