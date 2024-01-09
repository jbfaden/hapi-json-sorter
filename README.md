# hapi-json-sorter
Sorts hapi json responses into known order to facilitate comparisons.  GSON is the JSON 
library used, as it can be made to preserve insertion order (using a LinkedHashMap).

See also https://github.com/hapi-server/data-specification-schema/issues/9, which describes
the output needed:

* indents using 2-space indents
* json tag order should match the order which they are listed in the documentation
* each item of array has a separate line
* each tag of an object has a separate line
* each tag of an object does not have a space after the tag's name

See also https://github.com/hapi-server/data-specification/blob/master/hapi-3.1.0/HAPI-data-access-spec-3.1.0.md
