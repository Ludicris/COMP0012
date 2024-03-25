package comp0012.main;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Stack;
import java.util.HashMap;

import org.apache.bcel.classfile.ClassParser;
import org.apache.bcel.classfile.Code;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.*;
import org.apache.bcel.util.InstructionFinder;
import org.apache.bcel.classfile.ConstantPool;
import org.apache.bcel.classfile.Constant;
import org.apache.bcel.Constants;

public class ConstantFolder
{
	String classFilePath = null;
	ClassParser parser = null;
	ClassGen gen = null;

	JavaClass original = null;
	JavaClass optimized = null;

	Stack<Number> constantStack = null; // store all constants

	HashMap<Integer, Number> variableMap = null; // store variable index and constant in that variable

	Stack<InstructionHandle> pushAndLoadInstructions = null; // store all push and load instructions

	public ConstantFolder(String classFilePath)
	{
		System.out.println("Class: " + classFilePath);
		try{
			this.classFilePath = classFilePath;
			this.parser = new ClassParser(classFilePath);
			this.original = this.parser.parse();
			this.gen = new ClassGen(this.original);
		} catch(IOException e){
			e.printStackTrace();
		}
	}

	public void printConstants(ConstantPoolGen cpgen)
	{
		ConstantPool cp = cpgen.getConstantPool();
		int length = cp.getLength();

		for (int i = 1; i < length; i++) {
			Constant constant = cp.getConstant(i);

			// Check if the constant is of specific types: Integer, Long, Float, Double
			if (constant != null &&
					(constant.getTag() == Constants.CONSTANT_Integer || constant.getTag() == Constants.CONSTANT_Long || constant.getTag() == Constants.CONSTANT_Float || constant.getTag() == Constants.CONSTANT_Double)) {
				System.out.println("Constant #" + i + ": " + constant);
			}
		}
	}

	public void printInstructions(ClassGen cgen)
	{
		Method[] methods = cgen.getMethods();
		for (Method method : methods) {
			InstructionList il = new InstructionList(method.getCode().getCode());
			InstructionHandle[] handles = il.getInstructionHandles();

			System.out.println("Instructions for method: " + method.getName());

			for (InstructionHandle handle : handles) {
				System.out.println(handle.getInstruction());
			}
		}
	}

	public void optimize()
	{
		ClassGen cgen = new ClassGen(original);
		ConstantPoolGen cpgen = cgen.getConstantPool();

		System.out.println("---BEFORE OPTIMISATION---");
		printConstants(cpgen);
		printInstructions(cgen);

		// Implement your optimization here
		if (classFilePath.contains("ConstantVariableFolding")) {
			System.out.println("Start Optimisation");
			Method[] methods = cgen.getMethods();
			for (Method method : methods) {
				// initialize data structure for every method
				constantStack = new Stack<Number>();
				variableMap = new HashMap<Integer, Number>();
				pushAndLoadInstructions = new Stack<InstructionHandle>();
				performConstantVariableFolding(cgen, cpgen, method);
			}
		}

		System.out.println("---AFTER OPTIMISATION---");
		printConstants(cpgen);
		printInstructions(cgen);
        
		this.optimized = cgen.getJavaClass();
	}

	public void performConstantVariableFolding(ClassGen cgen, ConstantPoolGen cpgen, Method method)
	{
		// Get the method's instruction list of raw byte code
		InstructionList il = new InstructionList(method.getCode().getCode());

		MethodGen methodGen = new MethodGen(method.getAccessFlags(), method.getReturnType(), method.getArgumentTypes(), null, method.getName(), cgen.getClassName(), il, cpgen);

		InstructionHandle[] handles = il.getInstructionHandles();

		// iterate through all the instructions and perform optimisation according to their type
		for (InstructionHandle handle : handles) {
			Instruction instruction = handle.getInstruction();
			if (instruction instanceof ArithmeticInstruction) optimiseArithmeticInstruction(il, handle, cpgen);
			else if (instruction instanceof StoreInstruction) optimiseStoreInstruction(il, handle);
			else if (instruction instanceof LoadInstruction) optimiseLoadInstruction(il, handle, cpgen);
			else if (isConstantLoadOrPushInstruction(instruction)) optimiseConstantLoadOrPushInstruction(il, handle, cpgen);
			else if (instruction instanceof ConversionInstruction) optimiseConversionInstruction(il, handle, cpgen);
		}

		methodGen.setMaxStack();
		methodGen.setMaxLocals();
		Method optimisedMethod = methodGen.getMethod();
		cgen.replaceMethod(method, optimisedMethod);
	}

	public void optimiseArithmeticInstruction(InstructionList il, InstructionHandle handle, ConstantPoolGen cpgen)
	{
		System.out.println("---Optimising Arithmetic Instruction---");
		Instruction instruction = handle.getInstruction();
		// Calculate the result of arithmetic operation
		Number result = performArithmeticOperation(instruction);
		constantStack.push(result);
		deleteInstruction(il, pushAndLoadInstructions.pop());
		deleteInstruction(il, pushAndLoadInstructions.pop());
		Number value = constantStack.peek();
		// Load the result into the constant pool
		Instruction newLoadInstruction = generateNewLoadInstruction(value, cpgen);
		handle.setInstruction(newLoadInstruction);
		pushAndLoadInstructions.push(handle);
	}

	public Number performArithmeticOperation(Instruction operator)
	{
		Number second = constantStack.pop();
		Number first = constantStack.pop();
		Number result;

		// Integer Operations
		if (operator instanceof IADD) result = first.intValue() + second.intValue();
		else if (operator instanceof ISUB) result = first.intValue() - second.intValue();
		else if (operator instanceof IMUL) result = first.intValue() * second.intValue();
		else if (operator instanceof IDIV) result = first.intValue() / second.intValue();

		// Long Operations
		else if (operator instanceof LADD) result = first.longValue() + second.longValue();
		else if (operator instanceof LSUB) result = first.longValue() - second.longValue();
		else if (operator instanceof LMUL) result = first.longValue() * second.longValue();
		else if (operator instanceof LDIV) result = first.longValue() / second.longValue();

		// Float Operations
		else if (operator instanceof FADD) result = first.floatValue() + second.floatValue();
		else if (operator instanceof FSUB) result = first.floatValue() - second.floatValue();
		else if (operator instanceof FMUL) result = first.floatValue() * second.floatValue();
		else if (operator instanceof FDIV) result = first.floatValue() / second.floatValue();

		// Double Operations
		else if (operator instanceof DADD) result = first.doubleValue() + second.doubleValue();
		else if (operator instanceof DSUB) result = first.doubleValue() - second.doubleValue();
		else if (operator instanceof DMUL) result = first.doubleValue() * second.doubleValue();
		else if (operator instanceof DDIV) result = first.doubleValue() / second.doubleValue();

		else throw new IllegalArgumentException("Invalid Operator for Arithmetic Expression");

		return result;
	}

	public void optimiseStoreInstruction(InstructionList il, InstructionHandle handle)
	{
		System.out.println("---Optimising Store Instruction---");
		Instruction instruction = handle.getInstruction();
		int key = ((StoreInstruction) instruction).getIndex();
		Number value = constantStack.pop();
		// System.out.println("Insert into variableMap: " + "(" + key + ", " + value + ")");
		variableMap.put(key, value);
		pushAndLoadInstructions.pop();
	}

	public void optimiseLoadInstruction(InstructionList il, InstructionHandle handle, ConstantPoolGen cpgen)
	{
		Instruction instruction = handle.getInstruction();
		if (!(instruction instanceof ALOAD)) {
			System.out.println("---Optimising Load Instruction---");
			int key = ((LoadInstruction) instruction).getIndex();
			Number value = variableMap.get(key);
			constantStack.push(value);
			pushAndLoadInstructions.push(handle);
		}
	}

	public boolean isConstantLoadOrPushInstruction(Instruction instruction)
	{
		return (instruction instanceof ICONST || instruction instanceof LCONST ||
				instruction instanceof FCONST || instruction instanceof  DCONST ||
				instruction instanceof BIPUSH || instruction instanceof SIPUSH ||
				instruction instanceof LDC || instruction instanceof LDC2_W);
	}

	public void optimiseConstantLoadOrPushInstruction(InstructionList il, InstructionHandle handle, ConstantPoolGen cpgen)
	{
		System.out.println("---Optimising Constant Load and Push Instruction---");
		Number value = null;
		Instruction instruction = handle.getInstruction();
		if (instruction instanceof ICONST) value = ((ICONST) instruction).getValue();
		else if (instruction instanceof LCONST) value = ((LCONST) instruction).getValue();
		else if (instruction instanceof FCONST) value = ((FCONST) instruction).getValue();
		else if (instruction instanceof DCONST) value = ((DCONST) instruction).getValue();

		if (instruction instanceof BIPUSH) value = ((BIPUSH) instruction).getValue();
		else if (instruction instanceof SIPUSH) value = ((SIPUSH) instruction).getValue();
		else if (instruction instanceof LDC) value = (Number) ((LDC) instruction).getValue(cpgen);
		else if (instruction instanceof LDC2_W) value = ((LDC2_W) instruction).getValue(cpgen);
		constantStack.push(value);
		pushAndLoadInstructions.push(handle);
	}

	private static Instruction generateNewLoadInstruction (Number value, ConstantPoolGen cpgen)
	{
		Instruction newLoadInstruction;
		int newConstantInPool;
		if (value instanceof Integer) {
			int numInt = (Integer) value;
			boolean isInRange = numInt >= 0 && numInt <= 5;
			if (isInRange) {
				newLoadInstruction = new ICONST(numInt);
			} else {
				newConstantInPool = cpgen.addInteger(numInt);
				newLoadInstruction = new LDC(newConstantInPool);
			}
		}
		else if (value instanceof Long) {
			long numLong = (Long) value;
			newConstantInPool = cpgen.addLong(numLong);
			newLoadInstruction = new LDC2_W(newConstantInPool);
		}
		else if (value instanceof Float) {
			float numFloat = (Float) value;
			newConstantInPool = cpgen.addFloat(numFloat);
			newLoadInstruction = new LDC(newConstantInPool);
		}
		else if (value instanceof Double){
			double numDouble = (Double) value;
			newConstantInPool = cpgen.addDouble(numDouble);
			newLoadInstruction = new LDC2_W(newConstantInPool);
		}
		else throw new IllegalArgumentException("Invalid value to load");
		return newLoadInstruction;
	}

	public void optimiseConversionInstruction (InstructionList il, InstructionHandle handle, ConstantPoolGen cpgen)
	{
		Instruction instruction = handle.getInstruction();
		boolean toIntegerInstruction = instruction instanceof L2I || instruction instanceof F2I || instruction instanceof D2I;
		boolean toLongInstruction = instruction instanceof I2L || instruction instanceof F2L || instruction instanceof D2L;
		boolean toFloatInstruction = instruction instanceof I2F || instruction instanceof L2F || instruction instanceof D2F;
		boolean toDoubleInstruction = instruction instanceof I2D || instruction instanceof L2D || instruction instanceof F2D;

		Number value = constantStack.pop();
		Number convertedValue = null;
		if (toIntegerInstruction) convertedValue = value.intValue();
		else if (toLongInstruction) convertedValue = value.longValue();
		else if (toFloatInstruction) convertedValue = value.floatValue();
		else if (toDoubleInstruction) convertedValue = value.doubleValue();

		constantStack.push(convertedValue);
		deleteInstruction(il, pushAndLoadInstructions.pop());
		Number finalValue = constantStack.peek();
		// Load the result into the constant pool
		Instruction newLoadInstruction = generateNewLoadInstruction(finalValue, cpgen);
		handle.setInstruction(newLoadInstruction);
		pushAndLoadInstructions.push(handle);
	}

	public void deleteInstruction(InstructionList il, InstructionHandle handle)
	{
		InstructionHandle next = handle.getNext();
		try{
			System.out.println("Deleting Instruction: " + handle.getInstruction());
			il.delete(handle);
		} catch (TargetLostException e) {
			// handle goto referencing that involves the instruction
			InstructionHandle[] targets = e.getTargets();
			for (InstructionHandle target : targets){
				InstructionTargeter[] its = target.getTargeters();
				for (InstructionTargeter it : its) {
					it.updateTarget(target, next);
				}
			}
		}
	}
	
	public void write(String optimisedFilePath)
	{
		this.optimize();

		try {
			FileOutputStream out = new FileOutputStream(new File(optimisedFilePath));
			this.optimized.dump(out);
		} catch (FileNotFoundException e) {
			// Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// Auto-generated catch block
			e.printStackTrace();
		}
	}
}