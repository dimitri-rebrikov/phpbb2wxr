Prerequisites
==============
You need a JRE (Java Runtime Environment) installed on you machine.

Current implementation state
=============================
seems to work but not tested productive

Simple Workflow
================
This workflow is for you, if you just like to migrate all phpBB entries into a fresh WP installation

1. get a copy of the phpBB database and load it to the MySQL database on your local machine
2.(optional) edit databaseUrl, databaseUser, databasePwd, tablePrefix parameters in all *.groovy scripts
3. run "runGroovyScript.bat default.categories.groovy"
4. import the produced gategory hierarchy export file "default.categories.output.xml" as an WP export into your WP instance
5. inspect the created categories and their hierarchie
6. (optional) edit "post.info.txt" to give the migration script hints which posts are articles and wich are comments
7. run phpbb2wxr.bat
8. import produced files "phpbb2wxr.outputNNN.xml" into your WP instance as WP export files one after another
9. inspect the results in WP


Custom Workflow 
================
This workflow is for you, if you already have an working WP instance and just like to add the articles from phpBB to it

1. get a copy of the phpBB database and load it to the MySQL database on your local machine
2. (optional) edit databaseUrl, databaseUser, databasePwd, tablePrefix parameters in all *.groovy scripts
3. (optional) edit "post.info.txt" to give the migration script hints which posts are articles and wich are comments
4. (optional) fill either some forum ID's into filter.forums.txt or some topic ID's into filter.topics.txt to reduce the export amount
5. (optional) run "runGroovyScript.bat initial_forum.mapping.groovy" to put the default topic to WP category mapping into the forum.mapping.txt file
6. (optional) edit the forum.mapping.txt file to provide customized topic to WP category mapping
7. run phpbb2wxr.bat
8. inspect the produced files phpbb2wxr.outputNNN.xml
9. import the produced files into a test/non-productive instance of your Word Press
10. inspect the results in Word Press
11. (optional) repeat from #3 to improve the results
12. finally import the produced file into your productive Word Press instance