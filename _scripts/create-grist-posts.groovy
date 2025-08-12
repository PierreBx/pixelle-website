@Grab('org.codehaus.groovy.modules.http-builder:http-builder:0.7.1')
@Grab('org.apache.commons:commons-lang3:3.14.0')
@Grab('ch.qos.logback:logback-classic:1.4.7')
@GrabConfig(systemClassLoader=true)
import groovy.util.logging.Slf4j

import org.apache.commons.lang3.StringUtils
import groovy.json.*
import org.apache.groovy.json.internal.LazyMap
import java.text.SimpleDateFormat

@Slf4j
class GristConfig {

  static String apiKey   = '0e3eaf66d37894088e6aa62dce22696904775759'
  static String docId    = 'rt3QMc825NvK9dQCtjHfZt'
  static String baseUrl    = "http://localhost:8484/api/docs"

  static class BlogPeopleTable {
    static String tableId = "M20_PEOPLE"
    static String peopleIdColumn = "PeopleID"
  }

  static blogPeopleTable = new BlogPeopleTable()

  static String blogMovieTableId = "B10_FILMS"

  static fetchUniqueKeyFromGristTable(String tableId, String key, String value) {

    println("==> fetchUniqueKeyFromGristTable(fetching {tableId} {key} {value}  ")

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

    println "✅ Image name retrieved: ${imageName}"
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

    println "⬇ Downloading image: ${fileName} into ${imageDir}"
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

  static void writeMoviePosts(LazyMap record, GristConfig gristConfig) {

    def movie = record.fields
    println("=>movie: ${movie}")

    // ===  FRONT MATTER VARIABLES  ===

    // layout
    String front_layout = "post"

    // date
    String front_date = movie.PostDate ? Utility.formatTimestamp(movie.PostDate) : "1972-03-07"

    // slug
    String title = movie.Title ?: "untitled"
    String front_slug = Utility.slugify(title)

    // title
    String front_title = "${title} | film"

    // description
    String releaseYear = movie.ReleaseYear ? Utility.getYearFromUnix(movie.ReleaseYear) : ""
    def director = gristConfig.fetchUniqueKeyFromGristTable(gristConfig.blogPeopleTable.tableId,
      gristConfig.blogPeopleTable.peopleIdColumn,
      "${movie.Director}")
    String directorName = director.FullName
    println("==> director       ${director}")
    println("==> director name: ${directorName}")


    String front_description = "${directorName}  (${releaseYear})"

    // tags
    String front_tags = "[ movie ]"

    // category
    String front_category = "[ films ]"

    // ===  POST CONTENT  ===

    String content_text = movie.Text ?: ""

    // ===  POST MARKDOWN FILE CREATION  ===

    String filename = "${front_date}-${front_slug}"
    File postFile = new File(postsDirectory, "${filename}.md")

    // ===  POST ASSET DIRECTORY CREATION  ===

    File imageDir = new File(imagesDirectory, filename)
    imageDir.mkdirs()

    // === MAIN IMAGE RETRIEVING ===

    int mainAttachmentId = movie.PostPicture?.get(1)
    String imageName = gristConfig.getAttachmentName(mainAttachmentId)
    File mainImageFile = gristConfig.downloadAttachmentFromGrist(mainAttachmentId, "main-${imageName}",
      imageDir)
    String front_postimagename = mainImageFile.path

    // === GALLERY IMAGES RETRIEVING ===

    def galleryAttachments = movie.PostGallery ?: []
    galleryAttachments.eachWithIndex { galleryAttachmentId, idx ->
      if (idx > 0) {
        String galleryImageName = gristConfig.getAttachmentName(galleryAttachmentId as int)
        gristConfig.downloadAttachmentFromGrist(galleryAttachmentId as int,
          "gallery-${galleryImageName}" as String,
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
tags: ${front_tags}
categories: ${front_category}

---

${content_text}

${post_gallery}

"""

    println "✔ Post written (movie): ${postFile.name}"
  }

  static String addPictures(String imageDir, Boolean addPictures) {

    String result = ""

    if (addPictures) {
      def picturesDir = new File(imageDir)
      def files = picturesDir.listFiles().sort { it.name }
      files.each { file ->
        result += "\n\n![text](${file.path})\n"
        def caption = file.name.substring(0, file.name.lastIndexOf('.'))
        if (caption[0] != '(') { // title of the file in parenthesis => do not display
          result += "\n<div style=\"text-align: center;\"><i>${caption}</i></div>\n"
        }
        result += "\n<br><br>\n"

      }
      return result
    }
    return result
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

    records.each { jekyllConfig.writeMoviePosts(it as LazyMap, gristConfig) }
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
