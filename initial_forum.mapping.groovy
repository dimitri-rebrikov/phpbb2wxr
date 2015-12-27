import groovy.sql.*
import org.apache.log4j.Logger
import org.apache.commons.lang3.StringEscapeUtils

log = Logger.getLogger('')

tablePrefix = 'phpbb_'
tableForums = tablePrefix + 'forums'
tablePosts = tablePrefix + 'posts'

outputFile = new File('forum.mapping.txt')

log.info('connect to the phpbb database')
sql = Sql.newInstance('jdbc:mysql://localhost:3306/phpbb', 'root', '', 'org.gjt.mm.mysql.Driver')

outputFile.withWriter('UTF-8'){ writer ->

	writeFileBeginning(writer)
	def parent_name = null
	
	log.info('iterate over the forums in the posts table')
	sql.eachRow('select forum_id, forum_name, (select forum_name from ' + tableForums + ' where forum_id=f.parent_id) parent_name from ' + tableForums + ' f where exists(select * from ' + tablePosts + ' where forum_id=f.forum_id) order by parent_name, forum_name') { row ->
		cur_parent_name=StringEscapeUtils.unescapeHtml4(row.parent_name)
		cur_forum_name=StringEscapeUtils.unescapeHtml4(row.forum_name)
		if(parent_name != cur_parent_name) {
			parent_name = cur_parent_name
			writer << "#\r\n# $parent_name\r\n#\r\n"
		}
		writer << "$cur_forum_name:::${cur_forum_name.toUpperCase()}\r\n"
	}
}

def writeFileBeginning(writer) {
	writer << '''
#\r\n
# Format:\r\n
# <phpBB_forum_name>:::<WordPress_category_name>\r\n
#\r\n
#\r\n
'''
}