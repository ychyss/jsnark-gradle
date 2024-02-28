/*******************************************************************************
 * Author: Ahmed Kosba <akosba@cs.umd.edu>
 *******************************************************************************/
package com.jsnark.examples.generators.math;

import com.jsnark.circuit.eval.CircuitEvaluator;
import com.jsnark.circuit.structure.CircuitGenerator;
import com.jsnark.circuit.structure.Wire;
import com.jsnark.examples.gadgets.math.DotProductGadget;

public class DotProductCircuitGenerator extends CircuitGenerator {

	private Wire[] a;
	private Wire[] b;
	private int dimension;

	public DotProductCircuitGenerator(String circuitName, int dimension) {
		super(circuitName);
		this.dimension = dimension;
	}

	@Override
	protected void buildCircuit() {

		a = createInputWireArray(dimension, "Input a");
		b = createInputWireArray(dimension, "Input b");

		DotProductGadget dotProductGadget = new DotProductGadget(a, b);
		Wire[] result = dotProductGadget.getOutputWires();
		makeOutput(result[0], "output of dot product a, b");
	}

	@Override
	public void generateSampleInput(CircuitEvaluator circuitEvaluator) {

		for (int i = 0; i < dimension; i++) {
			circuitEvaluator.setWireValue(a[i], 10 + i);
			circuitEvaluator.setWireValue(b[i], 20 + i);
		}
	}

	public static void main(String[] args) throws Exception {

		DotProductCircuitGenerator generator = new DotProductCircuitGenerator("dot_product", 3);
		generator.generateCircuit();
		generator.evalCircuit();
		generator.prepFiles();
		generator.runLibsnark();	
	}

}
