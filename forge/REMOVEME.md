### Remove me when forge can boot without funny lazyModulePatcher

HOW TO GET FORGE BOOTIN:

Add in you run config VM options: at the -p argument
`;..\lazyModulePatcher.jar`

And also add the VM option: --add-exports
java.base/jdk.internal.access=me.ewoudje.lazy_module_patcher
