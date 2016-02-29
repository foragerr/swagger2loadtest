package net.foragerr.swagger2loadtest.loadrunner;

import io.swagger.codegen.CodegenConfig;
import io.swagger.codegen.CodegenType;
import io.swagger.codegen.DefaultCodegen;
import io.swagger.codegen.examples.ExampleGenerator;
import io.swagger.models.ArrayModel;
import io.swagger.models.Model;
import io.swagger.models.Operation;
import io.swagger.models.Path;
import io.swagger.models.RefModel;
import io.swagger.models.Swagger;
import io.swagger.models.parameters.BodyParameter;
import io.swagger.models.parameters.Parameter;
import io.swagger.models.properties.ArrayProperty;
import io.swagger.models.properties.MapProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class LoadRunnerCodegen extends DefaultCodegen implements CodegenConfig {
    @Override
    public CodegenType getTag() {
        return CodegenType.CLIENT;
    }

    @Override
    public String getName() {
        return "loadrunner";
    }

    @Override
    public String getHelp() {
        return "Generates a LoadRunner .c file.";
    }

    public LoadRunnerCodegen(){
        super();

        // set default output folder
        outputFolder = "generated-code/LoadRunner";

        /**
         * Api classes.  You can write classes for each Api file with the apiTemplateFiles map.
         * as with models, add multiple entries with different extensions for multiple files per
         * class
         */
        apiTemplateFiles.put( "script.mustache", ".c");
        
//		modelTemplateFiles.put("script.mustache", ".c");

        /**
         * Template Location.  This is the location which templates will be read from.  The generator
         * will use the resource stream to attempt to read the templates.
         */
        templateDir = "LoadRunner";

        /**
         * Reserved words.  Override this with reserved words specific to your language
         */
        reservedWords = new HashSet<String>(
                Arrays.asList(
                        "sample1",  //TODO use actual LR reserved words
                        "sample2")
        );
        
        /**
         * Additional Properties.  These values can be passed to the templates and
         * are available in models, apis, and supporting files
         */
        additionalProperties.put("apiVersion", apiVersion);
    }
    


    /**
     * Escapes a reserved word as defined in the `reservedWords` array. Handle escaping
     * those terms here.  This logic is only called if a variable matches the reseved words
     *
     * @return the escaped term
     */
    @Override
    public String escapeReservedWord(String name) {
        return "_" + name;  // add an underscore to the name
    }


    /**
     * Optional - type declaration.  This is a String which is used by the templates to instantiate your
     * types.  There is typically special handling for different property types
     *
     * @return a string value used as the `dataType` field for model templates, `returnType` for api templates
     */
    @Override
    public String getTypeDeclaration(Property p) {
      if(p instanceof ArrayProperty) {
        ArrayProperty ap = (ArrayProperty) p;
        Property inner = ap.getItems();
        return getSwaggerType(p) + "[" + getTypeDeclaration(inner) + "]";
      }
      else if (p instanceof MapProperty) {
        MapProperty mp = (MapProperty) p;
        Property inner = mp.getAdditionalProperties();
        return getSwaggerType(p) + "[String, " + getTypeDeclaration(inner) + "]";
      }
      return super.getTypeDeclaration(p);
    }

    /**
     * Optional - swagger type conversion.  This is used to map swagger types in a `Property` into 
     * either language specific types via `typeMapping` or into complex models if there is not a mapping.
     *
     * @return a string value of the type or complex model for this property
     * @see io.swagger.models.properties.Property
     */
    @Override
    public String getSwaggerType(Property p) {
      String swaggerType = super.getSwaggerType(p);
      String type = null;
      if(typeMapping.containsKey(swaggerType)) {
        type = typeMapping.get(swaggerType);
        if(languageSpecificPrimitives.contains(type))
          return toModelName(type);
      }
      else
        type = swaggerType;
      return toModelName(type);
    }
    
    // source folder where to write the files
    protected String sourceFolder = "";
    protected String apiVersion = "1.0.0";
    
    /**
     * Location to write model files.  You can use the modelPackage() as defined when the class is
     * instantiated
     */
    @Override
    public String modelFileFolder() {
      return outputFolder + "/" + sourceFolder + "/" + modelPackage().replace('.', File.separatorChar);
    }

    /**
     * Location to write api files.  You can use the apiPackage() as defined when the class is
     * instantiated
     */
    @Override
    public String apiFileFolder() {
      return outputFolder + "/" + sourceFolder + "/" + apiPackage().replace('.', File.separatorChar);
    }
    
    @Override
    public void preprocessSwagger(Swagger swagger) {

        final List<String> expectedTypes = Arrays.asList("application/json", "application/xml");
        final ExampleGenerator eg = new ExampleGenerator(swagger.getDefinitions());

        if (swagger != null && swagger.getPaths() != null) for (String pathname : swagger.getPaths().keySet()) {
            Path path = swagger.getPath(pathname);
            if (path.getOperations() != null) for (Operation operation : path.getOperations()) {
                List<Parameter> parameters = operation.getParameters();
                if (!parameters.isEmpty()) {
                    Parameter parameter = operation.getParameters().get(0); //FIXME making implicit assumption that there is only 1 parameter
                    if (parameter instanceof BodyParameter) {
                        BodyParameter bodyParam = (BodyParameter) parameter;
                        Model model = bodyParam.getSchema();
                        String simpleRef="";
                        if (model instanceof RefModel)
                            simpleRef = ((RefModel) model).getSimpleRef();
                        if (model instanceof ArrayModel)
                            simpleRef = ((RefProperty)((ArrayModel)model).getItems()).getSimpleRef();

                        List<Map<String, String>> examples = eg.generate(null, expectedTypes, new RefProperty(simpleRef));
                        for (Map<String, String> item : examples) {
                            String example = item.get("example");
                            final String contentType = item.get("contentType");

                            //System.out.println("\n\n ==================" + example);
                            //Format body string for LR
                            //1. escape all quotes
                            example = example.replaceAll("\"", "\\\\\"");
                            //2. Enclose each line in quotes
                            example = example.replaceAll("(.+)","\"$1\"");
                            //3. Add three tabs to all newlines
                            example = example.replaceAll("\n", "\t\t\t");
                            operation.setVendorExtension("x-request-example-" + contentType, example);
                            
                            //System.out.println("\n\n ++++++++++++++++" +example);
                        }
                    }
                }
            }
        }
    }


}
