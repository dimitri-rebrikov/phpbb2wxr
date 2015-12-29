import groovy.sql.*
import org.apache.log4j.Logger
import org.apache.commons.lang3.StringEscapeUtils

log = Logger.getLogger('')

tablePrefix = 'phpbb_'
tableForums = tablePrefix + 'forums'
tableTopics = tablePrefix + 'topics'
tablePosts = tablePrefix + 'posts'

outputFile = new File('forum.mapping.txt')

log.info('connect to the phpbb database')
sql = Sql.newInstance('jdbc:mysql://localhost:3306/phpbb', 'root', '', 'org.gjt.mm.mysql.Driver')

outputFile.withWriter('UTF-8'){ writer ->

	writeFileBeginning(writer)
	def parent_name = null
	
	log.info('iterate over the topics in the posts table')
	sql.eachRow('''
	select t.topic_id, t.topic_title topic_name, f.forum_id, f.forum_name, f.parent_id, pa.forum_name parent_name from ''' + tablePosts + ''' po
		left join ''' + tableTopics + ''' t on t.topic_id = po.topic_id
		left join ''' + tableForums + ''' f on f.forum_id = po.forum_id
		left join ''' + tableForums + ''' pa on pa.forum_id = f.parent_id
		group by t.topic_id, t.topic_title, f.forum_id, f.forum_name, f.parent_id, pa.forum_name
		order by parent_name, forum_name, topic_title
	''') { row ->
		cur_parent_name=StringEscapeUtils.unescapeHtml4(row.parent_name)
		cur_forum_name=StringEscapeUtils.unescapeHtml4(row.forum_name)
		cur_topic_name=StringEscapeUtils.unescapeHtml4(row.topic_name)
		if(parent_name != cur_parent_name) {
			parent_name = cur_parent_name
			writer << "\r\n#\r\n#\r\n# $parent_name\r\n#\r\n"
		}
		writer << "\r\n#$cur_parent_name -> $cur_forum_name -> $cur_topic_name\r\n$row.topic_id:::${cur_topic_name.toUpperCase()}\r\n"
	}
}

def writeFileBeginning(writer) {
	writer << '''
#\r\n
# Format:\r\n
# <phpBB_topic_ID>:::<WordPress_category_name>\r\n
#\r\n
#\r\n
'''
}