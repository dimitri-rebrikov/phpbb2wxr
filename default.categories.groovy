import groovy.sql.*
import org.apache.log4j.Logger
import org.apache.commons.lang3.StringEscapeUtils

log = Logger.getLogger('')

tablePrefix = 'phpbb_'
tableForums = tablePrefix + 'forums'
tableTopics = tablePrefix + 'topics'
tablePosts = tablePrefix + 'posts'

outputFileBeginning = '''
<rss version="2.0"
	xmlns:excerpt="http://wordpress.org/export/1.2/excerpt/"
	xmlns:content="http://purl.org/rss/1.0/modules/content/"
	xmlns:wfw="http://wellformedweb.org/CommentAPI/"
	xmlns:dc="http://purl.org/dc/elements/1.1/"
	xmlns:wp="http://wordpress.org/export/1.2/"
>
<channel>
	<title>wp</title>
	<link>http://localhost/wordpress</link>
	<description>Eine weitere WordPress-Seite</description>
	<pubDate>''' + formatPubDate() + '''</pubDate>
	<language>de-DE</language>
	<wp:wxr_version>1.2</wp:wxr_version>
	<wp:base_site_url>http://localhost/wordpress</wp:base_site_url>
	<wp:base_blog_url>http://localhost/wordpress</wp:base_blog_url>
'''

outputFileEnding = '''
   </channel>
</rss>
'''

outputFile = new File('default.categories.output.xml')

log.info('connect to the phpbb database')
sql = Sql.newInstance('jdbc:mysql://localhost:3306/phpbb', 'root', '', 'org.gjt.mm.mysql.Driver')

outputFile.withWriter('UTF-8'){ writer ->

	writer << outputFileBeginning
	def parent_name = null
	def forum_name = null
	
	def xml = new groovy.xml.MarkupBuilder(writer)
	
	log.info('iterate over the posts table')
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
			xml.'wp:category'{
				'wp:category_nicename'{ mkp.yieldUnescaped('<![CDATA[' +  getNiceName(parent_name, row.parent_id)  + ']]>') }
				'wp:cat_name'{ mkp.yield( parent_name ) }
			}
		}
		if(forum_name != cur_forum_name) {
			forum_name = cur_forum_name
			xml.'wp:category'{
				'wp:category_nicename'{ mkp.yieldUnescaped('<![CDATA[' +  getNiceName(forum_name, row.forum_id)  + ']]>') }
				'wp:cat_name'{ mkp.yield( forum_name ) }
				'wp:category_parent'{ mkp.yieldUnescaped('<![CDATA[' +  getNiceName(parent_name, row.parent_id)  + ']]>') }
			}
		}
		xml.'wp:category'{
			'wp:category_nicename'{ mkp.yieldUnescaped('<![CDATA[' + getNiceName(cur_topic_name, row.topic_id)  + ']]>') }
			'wp:cat_name'{ mkp.yield( cur_topic_name ) }
			'wp:category_parent'{ mkp.yieldUnescaped('<![CDATA[' + getNiceName(forum_name, row.forum_id)  + ']]>') }
		}
	}
	writer << outputFileEnding
}

def getNiceName(name, id) {
	return name.toLowerCase().replaceAll(/ä/,'ae').replaceAll(/ü/,'ue').replaceAll(/ö/,'oe').replaceAll(/ß/,'ss').replaceAll(/\W+/,'-') + '-' + id
}

def formatPubDate(date) {
	if(date == null) {
		date = new Date()
	}
	return date.format('EEE, dd MMM yyyy HH:mm:ss +0000')	
}