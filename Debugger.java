import com.sun.jdi.*;
import com.sun.jdi.connect.Connector;
import com.sun.jdi.connect.LaunchingConnector;
import com.sun.jdi.request.ClassPrepareRequest;
import com.sun.jdi.event.BreakpointEvent;
import com.sun.jdi.event.ClassPrepareEvent;
import com.sun.jdi.event.Event;
import com.sun.jdi.event.EventSet;
import com.sun.jdi.event.LocatableEvent;
import com.sun.jdi.request.BreakpointRequest;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Map;
import java.lang.String;


public class Debugger {
    private Class debugClass;
    private int[] breakPointLines;
    private JSONArray jsonResult;

    public Debugger(){
        JSONArray res = new JSONArray();
        this.setJsonResult(res);
    }

    public Class getDebugClass() {
        return debugClass;
    }

    public void setDebugClass(Class debugClass) {
        this.debugClass = debugClass;
    }

    public int[] getBreakPointLines() {
        return breakPointLines;
    }

    public void setBreakPointLines(int[] breakPointLines) {
        this.breakPointLines = breakPointLines;
    }

    public JSONArray getJsonResult() {return this.jsonResult; }

    public void setJsonResult(JSONArray jsonResult) {this.jsonResult = jsonResult; }


    /*
     * Create connection with VM
     * */
    public VirtualMachine connectAndLaunchVM() throws Exception {

        LaunchingConnector launchingConnector = Bootstrap.virtualMachineManager()
                .defaultConnector();
        Map<String, Connector.Argument> arguments = launchingConnector.defaultArguments();
        arguments.get("main").setValue(debugClass.getName());
        return launchingConnector.launch(arguments);
    }

    /*
     * Create ClassPrepareRequest to enable debugging
     * */
    public void enableClassPrepareRequest(VirtualMachine vm) {
        ClassPrepareRequest classPrepareRequest = vm.eventRequestManager().createClassPrepareRequest();
        classPrepareRequest.addClassFilter(debugClass.getName());
        classPrepareRequest.enable();
    }

    /*
     * Create breakpoint requests at each breakpoint
     * */
    public void setBreakPoints(VirtualMachine vm, ClassPrepareEvent event) {
        ClassType classType = (ClassType) event.referenceType();
        for(int lineNumber: breakPointLines) {
            try{
                Location location = classType.locationsOfLine(lineNumber).get(0);
                BreakpointRequest bpReq = vm.eventRequestManager().createBreakpointRequest(location);
                bpReq.enable();
            }catch(AbsentInformationException e){
                System.out.println(e);
            }
            catch(IndexOutOfBoundsException e){
                System.out.println(e);
            }

        }
    }

    /*
     * Collect debug information into JSON object
     * */
    public void writeVariablesToJSON(LocatableEvent event, int breakPoint) throws IncompatibleThreadStateException
    {
        JSONObject line =  new JSONObject();
        JSONObject values = new JSONObject();

        int[] breakpointLines = getBreakPointLines();

        try{
            Method method = event.location().method();

            StackFrame stackFrame = event.thread().frame(0);
            if(stackFrame.location().toString().contains(debugClass.getName())) {
                Map<LocalVariable, Value> visibleVariables = stackFrame.getValues(stackFrame.visibleVariables());
                for (Map.Entry<LocalVariable, Value> entry : visibleVariables.entrySet()) {
                    if (!entry.getKey().name().equals("args")) {
                        values.put(entry.getKey().name(), entry.getValue());

                    }
                }

                line.put("Line", stackFrame.location().toString());
                line.put("Function", method.name());
                line.put("Value", values);
                JSONArray result = getJsonResult();
                result.add(line);
                setJsonResult(result);

                System.out.println(line.toString());
            }

        }catch(AbsentInformationException e){
            System.out.println(e);
        }

    }

    /*
     * Write collected debug information to Output.json file
     * */
    public void fileWriter(JSONArray jResult) throws IOException{
        File file = new File("Output.json");
        //Gson gson = new GsonBuilder();

        if(file.isFile()){
            file.delete();
        }

        file.createNewFile();
        FileWriter writer = new FileWriter(file);

        if(jResult != null){
            writer.write(jResult.toJSONString());
        }

        writer.flush();
        writer.close();
    }

      /* Reads code from external file
       * Writes code written into java file
      */
    public static void codeWriter(String path) throws IOException{
        File classFile = new File("Debuggee.java");
        BufferedReader codeReader = new BufferedReader(new FileReader(classFile));
        String preCode = "";
        String postCode = "";
        Boolean classLine = false;
        String codeLine = codeReader.readLine();

        while( codeLine != null){
            if(codeLine.contains("class")){
                classLine = true;
            }
            else if(classLine){
                postCode += codeLine + "\n";
            }
            else{
                preCode += codeLine + "\n";
            }
            codeLine = codeReader.readLine();
        }
        codeReader.close();

        File codeFile = new File(path);
        BufferedReader reader = new BufferedReader(new FileReader(codeFile));
        String preContent = "";
        String postContent = "";
        Boolean classContent = false;
        String line = reader.readLine();

        while (line != null) {
            if(line.contains("class")){
                classContent = true;
            }
            else if(classContent){
                postContent += line + "\n";
            }
            else{
                preContent += line + "\n";
            }
            line = reader.readLine();
        }
        reader.close();

        preCode = preContent;
        postCode = postContent;

        String content =  preCode + "public class Debuggee {\n" + postCode;
        System.out.println(content);

        FileWriter writer = new FileWriter(classFile);
        writer.write(content);
        writer.flush();
        writer.close();

    }

    /*
     * Compile Debuggee.java file internally
     * */
    public static void compileClass() throws Exception{
        String command = "javac -g -cp \"tools.jar;json-simple-1.1.1.jar;.\" Debuggee.java";
        Process compiler = Runtime.getRuntime().exec(command);
        compiler.waitFor();
    }

    /* Console Arguments
     *  args[0] = String with ',' separated line numbers to act as breakpoints
     *  args[1] = file path for the input code
     */
    public static void main(String[] args) throws Exception {
        String[] breakpoint_params = args[0].split(",");
        String path = args[1];
        //String path = "./TestDebuggee.java";

        /*
        * Set breakpoints into integer array
        * */
        int breakPoints[] = new int[breakpoint_params.length];
        for(int i=0; i<breakPoints.length; i++){
            breakPoints[i] = Integer.parseInt(breakpoint_params[i].trim());
        }

        /*
        * Write the given file to the Debuggee file
        * */
        codeWriter(path);

        /*
        * Compile Debuggee.java file internally
        * */
        compileClass();

        Debugger debuggerInstance = new Debugger();
        debuggerInstance.setDebugClass(Debuggee.class);

        debuggerInstance.setBreakPointLines(breakPoints);
        VirtualMachine vm = null;
        int breakPoint = 0;
        
        try {
            vm = debuggerInstance.connectAndLaunchVM();
            debuggerInstance.enableClassPrepareRequest(vm);
            EventSet eventSet = null;
            while ((eventSet = vm.eventQueue().remove()) != null) {
                for (Event event : eventSet) {
                    System.out.println(event);
                    if (event instanceof ClassPrepareEvent) {
                        debuggerInstance.setBreakPoints(vm, (ClassPrepareEvent)event);
                    }
                    if (event instanceof BreakpointEvent) {
                        debuggerInstance.writeVariablesToJSON((BreakpointEvent) event, breakPoint);
                        breakPoint++;
                    }
                    vm.resume();
                }
            }
        } catch (VMDisconnectedException e) {
            System.out.println("Virtual Machine is disconnected.");
        } catch (Exception e) {
            e.printStackTrace();
        }
        JSONArray jResult = debuggerInstance.getJsonResult();
        debuggerInstance.fileWriter(jResult);
    }
}
