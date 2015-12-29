Prerequisites
==============
You need a JRE (Java Runtime Environment) installed on you machine.

Current implementation state
=============================
iteration over forums and topics
export post
export comments
export categories

Next stepts
============
test/bug fixing
probably the forum to category translations

Workflow
===================
1. get a copy of the phpBB database and load it to the MySQL database on your local machine
2. (optiona) edit databaseUrl, databaseUser, databasePwd, tablePrefix parameters in the phpbb2wxr.groovy scirpt
3. (optional) fill either some forum ID's into filter.forums.txt or some topic ID's into filter.topics.txt to reduce the export amount
4. (optional) run "runGroovyScript.bat initial_forum.mapping.groovy" to put the default topic to WP category mapping into the forum.mapping.txt file
5. (optional) edit the forum.mapping.txt file to provide customized topic to WP category mapping
4. run phpbb2wxr.bat
5. inspect the produced file phpbb2wxr.output.xml
6. import the produced file into a test/non-productive instance of your Word Press
7. inspect the results in Word Press
8. (optional) repeat from #2 to improve the results