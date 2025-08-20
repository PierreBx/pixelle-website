@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.1')
@Grab('org.apache.commons:commons-lang3:3.14.0')
@Grab('ch.qos.logback:logback-classic:1.4.7')
@GrabConfig(systemClassLoader=true)
import groovy.util.logging.Slf4j
import org.apache.commons.lang.text.StrBuilder
import org.apache.commons.lang3.StringUtils
import groovy.json.*
import org.apache.groovy.json.internal.LazyMap
import java.text.SimpleDateFormat

@Slf4j
class GristConfig {

  static String apiKey   = '0e3eaf66d37894088e6aa62dce22696904775759'
  static String docId    = 'rt3QMc825NvK9dQCtjHfZt'
  static String baseUrl    = "http://localhost:8484/api/docs"

  static class BlogTagTable {
    static String tableId = "A20_TAGS"
    static String idColumn = "id"
    static String nameColumn = "Name"
  }

  static blogTagTable = new BlogTagTable()

  static String blogMovieTableId = "B10_BLOGPOSTS"

  static fetchUniqueKeyFromGristTable(String tableId, String key, String value) {

    //println("==> fetchUniqueKeyFromGristTable(fetching {tableId} {key} {value}  ")

    String filter = "{ \"${key}\": [ \"${value}\" ] } "
    def urlEncodedFilter = URLEncoder.encode(filter, "UTF-8")
    String url = "${baseUrl}/${docId}/tables/${tableId}/records?filter=${urlEncodedFilter}"


    println("==> fetchUniqueKeyFromGristTable filter: ${filter}")
    println("==> fetchUniqueKeyFromGristTable url: ${url}")

    def connection = new URL(url).openConnection()
    connection.setRequestProperty("Authorization", "Bearer ${apiKey}")

    def json = new JsonSlurper().parse(connection.inputStream)

    return json.records[0].fields
  }

  static fetchUniqueRecordByID(String tableId, /* String key = 'id', */ int id) {

    //println("==> fetchUniqueRecordByID(fetching {tableId}  {value}  ")

    String filter = "{ \"id\": [ ${id} ] } "
    def urlEncodedFilter = URLEncoder.encode(filter, "UTF-8")
    String url = "${baseUrl}/${docId}/tables/${tableId}/records?filter=${urlEncodedFilter}"


    //println("==> fetchUniqueRecordByID filter: ${filter}")
    //println("==> fetchUniqueRecordByID url: ${url}")

    def connection = new URL(url).openConnection()
    connection.setRequestProperty("Authorization", "Bearer ${apiKey}")

    def json = new JsonSlurper().parse(connection.inputStream)

    return json.records[0].fields
  }

  static String fetchValuesByIdList(arrayList, String tableId, String columnName) {
     // arrayList : type ArrayList<Sting>


    //println("==> fetchValuesByIdList: arrayList: ${arrayList}   size: ${arrayList.size()}")

    if (arrayList && arrayList.size() > 1) {

      def values = arrayList.collect { id ->
        //print("     fetching value num: ${id}  ")
        def fields = fetchUniqueRecordByID(tableId, id as int)
        fields?[columnName] ?: "(unknown)"
      }

      def fvalues = values.join(", ")
      //println("     fvalues: ${fvalues}")
      return fvalues
    }


  }

  static String getAttachmentName(int attachmentId) {

    //println "⬇ Retrieving image name for attachment id: ${attachmentId}"

    def url = "${baseUrl}/${docId}/attachments/${attachmentId}"
    def connection = new URL(url).openConnection()
    connection.setRequestProperty("Authorization", "Bearer ${apiKey}")

    // Parse the response from the API
    def jsonResponse = new JsonSlurper().parse(connection.inputStream)
    //println(jsonResponse)

    // Extract the image name
    def imageName = jsonResponse.fileName

    //println "✅ Image name retrieved: ${imageName}"
    return imageName
  }

  static fetchRecords(String tableId) {

    def url = "${baseUrl}/${docId}/tables/${tableId}/records"
    //println(url)
    def connection = new URL(url).openConnection()
    connection.setRequestProperty("Authorization", "Bearer ${apiKey}")
    def json = new JsonSlurper().parse(connection.inputStream)
    return json.records
  }

  static File downloadAttachmentFromGrist(int attachmentId, String fileName, File imageDir) {

    File imageFile = new File(imageDir, fileName)

    // println "⬇ Downloading image: ${fileName} into ${imageDir}"
    def url = "${baseUrl}/${docId}/attachments/${attachmentId}/download"
    def connection = new URL(url).openConnection()
    connection.setRequestProperty("Authorization", "Bearer ${apiKey}")


    imageFile.withOutputStream { out ->
      connection.inputStream.withStream { input -> out << input }
    }

    return imageFile
  }



}

@Slf4j
class JekyllConfig {

  static String postsDir   = "_posts"
  static String imagesDir    = "assets/media"

  static File postsDirectory
  static File imagesDirectory

  static void createDirectories() {
    postsDirectory = new File(postsDir)
    imagesDirectory= new File(imagesDir)
    postsDirectory.mkdirs()
    imagesDirectory.mkdirs()
  }

  static void writeBlogPosts(LazyMap record, GristConfig gristConfig) {

    def blog = record.fields
    print("   > writing blog: ${blog.Title}")

    // ===  FRONT MATTER VARIABLES  ===

    // layout
    String front_layout = "post"

    // date
    String front_date = blog.Date ? Utility.formatTimestamp(blog.Date) : "1972-03-07"

    // post title
    String title = blog.Title ?: "untitled"

    // post slug
    String front_slug = Utility.slugify(title)

    // front title = post title | post type
    String type = blog.Type ?: ""
    String front_title = "${title} | ${type}" as String

    // description
    String front_description = blog.Description ?: ""

    // category
    def categoryRecord = GristConfig.fetchUniqueRecordByID("A10_CATEGORIES", blog.Category)
    def categoryName = categoryRecord.Name
    print(" category:  ${categoryName} ")

    String front_category = "[ ${categoryName} ]"

    // tags

    blog.Tags.remove(0) // removes first "L"
    println("blog.tags: ${blog.Tags} blog.tags.type : ${blog.Tags.getClass()}")


    String tags = gristConfig.fetchValuesByIdList(blog.Tags, gristConfig.blogTagTable.tableId,
                                                             gristConfig.blogTagTable.nameColumn)

    // ===  POST CONTENT  ===

    String content_text = blog.Text ?: ""

    // ===  POST MARKDOWN FILE CREATION  ===

    String filename = "${front_date}-${front_slug}"
    File postFile = new File(postsDirectory, "${filename}.md")

    // ===  POST ASSET DIRECTORY CREATION  ===

    File imageDir = new File(imagesDirectory, filename)
    imageDir.mkdirs()

    // === MAIN IMAGE RETRIEVING ===

    int mainAttachmentId = blog.Picture?.get(1)
    String imageName = gristConfig.getAttachmentName(mainAttachmentId)
    File mainImageFile = gristConfig.downloadAttachmentFromGrist(mainAttachmentId, imageName,
      imageDir)
    String front_postimagename = mainImageFile.path

    // === GALLERY IMAGES RETRIEVING ===

    def galleryAttachments = blog.Gallery ?: []
    galleryAttachments.eachWithIndex { galleryAttachmentId, idx ->
      if (idx > 0) {
        String galleryImageName = gristConfig.getAttachmentName(galleryAttachmentId as int)
        gristConfig.downloadAttachmentFromGrist(galleryAttachmentId as int,
          galleryImageName as String,
          imageDir)
      }
    }

    // === POST GALLERY CONTENT CREATION  ==
    String post_gallery = addPictures(imageDir.toString(), true)

    // === FRONTMATTER ==

    postFile.text = """---
layout: ${front_layout}
date: ${front_date}
slug: ${front_slug}
title: ${front_title}
description: ${front_description}
image: ${front_postimagename}
tags: [ ${tags} ]
categories: ${front_category}

---

${content_text}

${post_gallery}

"""

    println "  ✔ Post written (blog): ${postFile.name}"
  }

  // Helper: tiny HTML escaper for captions
  private static String escapeHtml(String s) {
    if (s == null) return ""
    s.replace("&","&amp;")
      .replace("<","&lt;")
      .replace(">","&gt;")
      .replace("\"","&quot;")
      .replace("'","&#39;")
  }

// Main function
  static String addPictures(String imageDir, Boolean addPictures) {
    if (!addPictures) return ""

    File picturesDir = new File(imageDir)
    if (!picturesDir.isDirectory()) return ""

    // Allowed image extensions (lowercase)
    final List<String> exts = [".jpg", ".jpeg", ".png", ".webp", ".gif", ".avif"]

    StringBuilder out = new StringBuilder()

    // List, filter, sort
    File[] files = picturesDir.listFiles()
    if (files == null) return ""

    files.findAll { f ->
      f.isFile() &&
        !f.name.startsWith(".") &&
        exts.any { ext -> f.name.toLowerCase().endsWith(ext) } &&
        !f.name.toLowerCase().contains("_preview")      // <-- skip previews
    }
      .sort { a, b -> a.name <=> b.name }
      .each { file ->
        // Caption = filename without extension (robust if no dot)
        String name = file.name
        int dot = name.lastIndexOf('.')
        String caption = (dot > 0) ? name.substring(0, dot) : name

        // Markdown image path with forward slashes (Jekyll-friendly)
        String webPath = file.path.replace(File.separator, "/")

        out.append("\n\n![text](${webPath})\n")

        // If caption begins with '(', skip title (your existing rule)
        if (caption && caption.charAt(0) != '(') {
          // Optional prettify: underscores/dashes → spaces
          String pretty = caption.replace('_',' ').replace('-',' ')
          out.append("\n<div style=\"text-align: center;\"><i>")
            .append(escapeHtml(pretty))
            .append("</i></div>\n")
        }

        out.append("\n<br><br>\n")
      }

    return out.toString()
  }

}

@Slf4j
class createPosts {

  static void run() {

    // create Jekyll directories
    def jekyllConfig = new JekyllConfig()
    jekyllConfig.createDirectories()

    // create Grist config object
    def gristConfig = new GristConfig()

// === MAIN EXECUTION ===

    def records = gristConfig.fetchRecords(GristConfig.blogMovieTableId)

    records.each { jekyllConfig.writeBlogPosts(it as LazyMap, gristConfig) }
  }

}

@Slf4j
class Utility {

  static String formatTimestamp(long timestamp) {
    Date date = new Date(timestamp * 1000) // Convert seconds to milliseconds
    return new SimpleDateFormat("yyyy-MM-dd").format(date)
  }

  static String getYearFromUnix(long unixTimestamp) {
    def date = new Date(unixTimestamp * 1000)  // Java expects milliseconds
    return date.format("yyyy")  // Return year in YYYY format
  }

  static String slugify(String str) {
    StringUtils.stripAccents(str).toLowerCase()
      .replaceAll(/[^a-z0-9]+/, "-")
      .replaceAll(/^-+|-+$/, "")
  }

}

System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "debug")

createPosts.run()
