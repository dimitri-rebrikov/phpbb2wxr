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

Tips&Tricks
=============

Export file size
----------------
The maximal file size you can import into WP ist normally restricted to 2MB. 
This is not the restriction of WP istself but of the PHP framework it runs on.
If you export size produced by phpbb2wxr exeed this restriction you have 2 choices:

1. reduce the parameter "outputFileMaxSize" in the phpbb2wxr.groov to 2MB (i.e. "outputFileMaxSize=2097152" because the parameter uses byte value). 
This will lead to production of several output files which you can import one after another

2. exceed the max upload file size in PHP by changing of the following 2 parameters in the php.ini on your web server upload_max_filesize and post_max_size 
Example:
upload_max_filesize = 64M
post_max_size = 64M

Automatic post to user assignment
----------------------------------
Testing on WP 4.4 I detecting a confusing behaviour in case you import serveral files.
During the import of the first file the missing users will be automatic created if you dont provide the assignment during the import dialog.
During the import of the following files you HAVE to provide the user assigment for the users which was automatically created during the import of previous files even the names are exactly the same.
Otherwise the posts will be assigned to the default user.