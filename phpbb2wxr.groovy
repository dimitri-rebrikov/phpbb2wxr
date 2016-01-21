import groovy.sql.*
import org.apache.log4j.Logger
import org.apache.commons.lang3.StringEscapeUtils

// max export file size in bytes
outputFileMaxSize=67108864

databaseUrl = 'jdbc:mysql://localhost:3306/phpbb'
databaseUser = 'root'
databasePwd = ''
tablePrefix = 'phpbb_'

tableForums = tablePrefix + 'forums'
tableTopics = tablePrefix + 'topics'
tablePosts = tablePrefix + 'posts'
tableUsers = tablePrefix + 'users'

outputFileMask = 'phpbb2wxr.output%03d.xml'
postInfoFile = 'post.info.txt'

forumIdFilterFile = 'filter.forums.txt'
topicIdFilterFile = 'filter.topics.txt'
topicToCategoryMappingFile = 'forum.mapping.txt'

log = Logger.getLogger('')

phpBbLinkPrefix='/forum/viewtopic.php?p='

forumIdFilter = loadIds(forumIdFilterFile)
topicIdFilter = loadIds(topicIdFilterFile)

postIdSet = [] as Set
commentIdToPostIdMap = [:]
uniquePostTitleMap = [:]
topicToCategoryMapping = [:]

loadPostInfo(postInfoFile)
loadTopicToCategoryMapping(topicToCategoryMappingFile)

log.info('connect to the phpbb database')
sql = Sql.newInstance(databaseUrl, databaseUser, databasePwd, 'org.gjt.mm.mysql.Driver')

outputFileCounter=1
outputFileSizeCounter=0
outputFileStream=null


outputFileBeginning = ('''
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
''').getBytes()

outputFileEnding = ('''
   </channel>
</rss>
''').getBytes()

log.info('iterate over the forums table')
sql.eachRow('select forum_id from ' + tableForums + ' order by forum_id ') { frow ->
	if(!isForumToTake(frow.forum_id)) {
		log.trace("Forum ID $frow.forum_id is not to take -> ignore it.")
		return
	}
	def forum = getForum(frow.forum_id)
	log.trace("Forum $forum.id \"$forum.name\"")
	sql.eachRow('select topic_id from ' + tableTopics + ' where forum_id = ? order by topic_id', [forum.id]) { trow ->
		if(!isTopicToTake(trow.topic_id)) {
			log.trace("Topic ID $trow.topic_id is not to take -> ignore it.")
			return
		}
		def topic = getTopic(trow.topic_id)
		log.trace("Topic $topic.id \"$topic.name\"")
		def postCommentMap = [:]
		def curPostId = null
		def curCommentIdSet = [] as Set
		sql.eachRow('select post_id, post_subject from ' + tablePosts + ' where topic_id = ? order by post_time', [topic.id]) { prow ->
			log.trace("Post $prow.post_id \"$prow.post_subject\"")
			if(postIdSet.contains(prow.post_id) || !(commentIdToPostIdMap.containsKey(prow.post_id) || isCommentSubject(prow.post_subject))) {
				log.trace("$prow.post_id is a POST")
				if(curPostId != null) {
					log.trace('we have new post arriving so save the current post with it comments')
					postCommentMap[curPostId] = curCommentIdSet.clone()
					curCommentIdSet.clear()
				}
				curPostId = prow.post_id
			} else {
				log.trace("$prow.post_id is a COMMENT")
				curCommentIdSet.add(prow.post_id)
			}
		}
		if(curPostId != null) {
			log.trace('put the last post')
			postCommentMap[curPostId] = curCommentIdSet.clone()
		}
		writePostItems(postCommentMap)
	}
	
}
outputFileStream?.close()

def loadIds(file) {
	set = [] as Set
	new File(file).eachLine{ line ->
		line = line.trim()
		if(line =~ /^\s*#/  || line =~ /^\s*$/) {
			return
		}
		log.trace("load ID: $line from $file")
		set.add(line as int)
	}
	return set
}	

def isCommentSubject(subject) {
	return subject =~ /^\s*Re:.*$/
}

def getForum(id) {
	def row = sql.firstRow('select * from ' + tableForums + ' where forum_id=?', [id])
	def forum = [id:row.forum_id, name:row.forum_name]
	return forum
}

def getTopic(id) {
	def row = sql.firstRow('select * from ' + tableTopics + ' where topic_id=?', [id])
	def topic = [id:row.topic_id, name:row.topic_title]
	return topic
}

def getPost(id) {
	def row = sql.firstRow('select * from ' + tablePosts + ' where post_id=?', [id])
	def post = [
		id:row.post_id, 
		topic_id:row.topic_id,
		forum_id:row.forum_id,
		title:StringEscapeUtils.unescapeHtml4(row.post_subject),
		content:StringEscapeUtils.unescapeHtml4(row.post_text.replaceAll(/[\x00-\x08,\x0B,\x0C,\x0E-\x1F,\x7f]/,'')),
		creator:getUserName(row.poster_id),
		creator_ip:row.poster_ip,
		date:new Date(row.post_time * 1000)
	]
	return post
}

def getUserName(id) {
	def row = sql.firstRow('select * from ' + tableUsers + ' where user_id=?', [id])
	return row?.username
}

def writePostItems(postCommentMap) {
	postCommentMap.each{ postId, commentIdSet ->
		def writer = new CharArrayWriter()
		writePostItem(postId, commentIdSet, writer)
		writeToOutputFile(writer.toString().getBytes())
	}
}

def writeToOutputFile(byteArr) {
	log.trace("write ${byteArr.length} bytes to the output file")
	def minFileSize = byteArr.length + outputFileBeginning.length + outputFileEnding.length
	if( minFileSize > outputFileMaxSize) {
		throw new RuntimeException("Cannot write to the file as the resulting file size $minFileSize exceed the max file size $outputFileMaxSize")
	}
	if(outputFileSizeCounter + byteArr.length + outputFileBeginning.length > outputFileMaxSize) {
		log.debug("close the existing output file as the current string size exeed the max file size")
		outputFileStream.write(outputFileEnding)
		outputFileStream.close()
		outputFileStream = null
		outputFileCounter++
	}
	if(outputFileStream == null) {
		def fileName = String.format(outputFileMask, outputFileCounter)
		log.debug("create new output file $fileName")
		outputFileStream = new FileOutputStream(fileName)
		outputFileStream.write(outputFileBeginning)
		outputFileSizeCounter = outputFileBeginning.length
	}
	outputFileStream.write(byteArr)
	outputFileSizeCounter += byteArr.length
}

def reformatPost(post) {
	def repl
	while(true){
		//log.trace("reformat post " + post)
		repl = reformatPostSingleRun(post)
		if(repl == post) {
		 break
		 }
		post = repl
	}
	return repl
}
def reformatPostSingleRun(post) {
    post=post.replaceAll(/(?s)\[size=\d\d\d(:.*?)\](.+?)\[\/size\1\]/,'<h5>$2</h5>')
	post=post.replaceAll(/(?s)\[size=\d\d\d\](.+?)\[\/size\]/,'<h5>$1</h5>')
    post=post.replaceAll(/(?s)\[size=\d\d(:.*?)\](.+?)\[\/size\1\]/,'<h6>$2</h6>')
	post=post.replaceAll(/(?s)\[size=\d\d\](.+?)\[\/size\]/,'<h6>$1</h6>')
    post=post.replaceAll(/(?s)\[color=(.+?)(:.*?)\](.+?)\[\/color\2\]/,'<span style="color: $1;">$3</span>')
	post=post.replaceAll(/(?s)\[color=(.+?)\](.+?)\[\/color\]/,'<span style="color: $1;">$2</span>')
    post=post.replaceAll(/(?s)\[i(:.*?)\](.+?)\[\/i\1\]/,'<em>$2</em>')
	post=post.replaceAll(/(?s)\[i\](.+?)\[\/i\]/,'<em>$1</em>')
    post=post.replaceAll(/(?s)\[b(:.*?)\](.+?)\[\/b\1\]/,'<strong>$2</strong>')
	post=post.replaceAll(/(?s)\[b\](.+?)\[\/b\]/,'<strong>$1</strong>')
    post=post.replaceAll(/(?s)\[u(:.*?)\](.+?)\[\/u\1\]/,'<span style="text-decoration: underline;">$2</span>')
	post=post.replaceAll(/(?s)\[u\](.+?)\[\/u\]/,'<span style="text-decoration: underline;">$1</span>')
    post=post.replaceAll(/(?s)\[quote(:.*?)\](.+?)\[\/quote\1\]/,'<blockquote>$2</blockquote>')
	post=post.replaceAll(/(?s)\[quote\](.+?)\[\/quote\]/,'<blockquote>$1</blockquote>')
    post=post.replaceAll(/(?s)\[quote="(.+?)"(:*.*?)\](.+?)\[\/quote\2\]/,'<blockquote><cite>$1</cite>:\r\n$3</blockquote>')
	post=post.replaceAll(/(?s)\[quote="(.+?)"\](.+?)\[\/quote\]/,'<blockquote><cite>$1</cite>:\r\n$2</blockquote>')
    post=post.replaceAll(/(?s)\[url=(.+?)(:.*?)\](.+?)\[\/url\2\]/,'<a href="$1" target="_blank">$3</a>')
	post=post.replaceAll(/(?s)\[url=(.+?)\](.+?)\[\/url\]/,'<a href="$1" target="_blank">$2</a>')
    post=post.replaceAll(/(?s)\[url(:.*?)\](.+?)\[\/url\1\]/,'<a href="$2" target="_blank">$2</a>')
	post=post.replaceAll(/(?s)\[url\](.+?)\[\/url\]/,'<a href="$1" target="_blank">$1</a>')
	post=post.replaceAll(/(?s)(<a\s+.*?href="([^"]+)"[^>]*>)http:.+?<\/a>/,'$1$2</a>')
	post=post.replaceAll(/(?s)(<a\s+.*?)onclick=".+?"(.*?>.+?<\/a>)/,'$1$2')
	return post
}

def createPostName(title, id) {
	return getNiceName(title)+'-'+id
}

def getNiceName(name) {
	return name.toLowerCase().replaceAll(/ä/,'ae').replaceAll(/ü/,'ue').replaceAll(/ö/,'oe').replaceAll(/ß/,'ss').replaceAll(/\W+/,'-')
}


def formatPubDate(date) {
	if(date == null) {
		date = new Date()
	}
	return date.format('EEE, dd MMM yyyy HH:mm:ss +0000')	
}

def getUniquePostTitle(post) {
	def key = post.date.format('yyyyMMdd') + post.title
	def cnt = uniquePostTitleMap[key] 
	cnt = cnt == null ? 0 : cnt
	cnt++
	uniquePostTitleMap[key] = cnt
	if(cnt == 1) {
		return post.title
	} else {
		return post.title + " ($cnt)"
	}
}

def loadPostInfo(file) {
	new File(file).eachLine{ line ->
		line = line.trim()
		if(line =~ /^\s*#/ || line =~ /^\s*$/) {
			return
		}
		log.trace("post info line \"$line\"")
		def arr = line.toUpperCase().split(/:/)
		def type = arr[1]
		if(type == 'B') {
			def postId = arr[0] as int
			log.trace("found post id $postId")
			postIdSet.add(postId)
		} else if(type == 'K') {
			def commentId = arr[0] as int
			def postId = arr[2] as int
			log.trace("found comment id $commentId for the post id $postId")
			commentIdToPostIdMap[commentId]=postId
		} else {
			throw new RuntimeException("type $type is unknown in \"$line\"")
		}
	}
}

def loadTopicToCategoryMapping(fileName) {
	def file = new File(fileName)
	if(!file.exists()) {
		log.warn("file \"$fileName\" does not exist! So assume there is NO special forum to categorie mapping.")
		return
	}
	file.eachLine{ line ->
		line = line.trim()
		if(line =~ /^\s*#/ || line =~ /^\s*$/) {
			return
		}
		log.trace("forum to category mapping line \"$line\"")
		def arr = line.split(/:::/)
		def topicId = arr[0].trim() as int
		def category = arr.length > 1 ? arr[1].trim() : ""
		log.trace("forum to category mapping $topicId -> \"$category\"")
		topicToCategoryMapping[topicId]=category
	}
}



def isForumToTake(forumId) {
	if(forumIdFilter.size()>0 && !forumIdFilter.contains(forumId)) {
		log.trace("Forum ID $forumId is not in the forum ID filter list -> do not take it")
		return false
	} else {
		log.trace("Forum ID $forumId is in the forum ID filter list or the list is empty -> take it")
		return true
	}
}

def isTopicToTake(topicId) {
	if(topicIdFilter.size()>0 && !topicIdFilter.contains(topicId)) {
		log.trace("Tipic ID $topicId is not in the forum ID filter list -> do not take it")
		return false
	}
	def category = topicToCategoryMapping[topicId]
	if(category == null || category.trim().size() > 0) {
		log.trace("the topic $topicId is either not in the mapping list or has a category mapping -> take it")
		return true
	} else {
		log.trace("empty entry for the topic $topicId in the topic to category mapping -> do not take it")
		return false
	}
}

def writePostItem(postId, commentIdSet, writer) {
	log.trace("write export for the post $postId")
	def post = getPost(postId)
	def xml = new groovy.xml.MarkupBuilder(writer)
	xml.item{
		title{mkp.yieldUnescaped('<![CDATA[' + getUniquePostTitle(post) + ']]>')}
		writeCategory(post.topic_id, xml)
		pubDate(formatPubDate(post.date))
		guid('phpbb_p' + post.id)
		link(phpBbLinkPrefix + post.id)
		'wp:post_name'(createPostName(post.title, post.id))
		'wp:post_id'(post.id)
		'dc:creator'(post.creator)
		'wp:post_date'(post.date?.format('yyyy-MM-dd HH:mm:ss'))
		'content:encoded'{mkp.yieldUnescaped('<![CDATA[' + reformatPost(post.content) + ']]>')}
		'wp:post_type'('post')
		'wp:comment_status'('open')
        'wp:ping_status'('open')
        'wp:status'('publish')
        'wp:post_parent'(0)
        'wp:menu_order'(0)
		'wp:is_sticky'(0)
		commentIdSet.each{ commentId ->
			writeCommentItem(commentId, xml)
		}
	}
	writer << '\n'
	log.trace("wrote export for the post $postId")
}

def writeCommentItem(commentId, xml) {
	log.trace("write export for the comment $commentId")
	def comment = getPost(commentId)
	xml.'wp:comment'{
			'wp:comment_id'(comment.id)
			'wp:comment_author'(comment.creator)
			'wp:comment_author_IP'(comment.creator_ip)
			'wp:comment_date'(comment.date?.format('yyyy-MM-dd HH:mm:ss'))
			'wp:comment_content'{mkp.yieldUnescaped('<![CDATA[' + reformatPost(comment.content) + ']]>')}
			'wp:comment_approved'(1)
			'wp:comment_type'()
			'wp:comment_parent'(0)
	}
	log.trace("wrote export for the comment $commentId")
}

def writeCategory(topic_id, xml) {
	log.trace("write export category for the forum $topic_id")
	def name = topicToCategoryMapping[topic_id]
	def niceName
	log.trace("category for the topic $topic_id: \"$name\"")
	if(name == null || name.trim().size() == 0) {
		def topic = getTopic(topic_id)
		name = topic.name.toUpperCase()
		log.trace("did not found the category for the topic $topic_id so generate the own one \"$name\"")
		niceName = getNiceName(name)+'-'+topic_id
		log.trace("generate nice name \"$niceName\" for \"$name\"")
	} else {
		niceName = getNiceName(name)
		log.trace("use nice name \"$niceName\" for \"$name\"")
	}
	
	xml.category(domain:"category", nicename:niceName) {
		mkp.yieldUnescaped('<![CDATA[' + name + ']]>')
	}
}
