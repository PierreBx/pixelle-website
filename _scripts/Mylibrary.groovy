import com.sun.imageio.plugins.common.SimpleRenderedImage
@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1')

import groovyx.net.http.*
import static groovyx.net.http.ContentType.*
import static groovyx.net.http.Method.*





static String addMap(Object map)                     {   // [51.505, -0.09]
                                                         // 'A pretty CSS3 popup.<br> Easily customizable.'
  def location = map.markers[0].coordinates
  def text = map.markers[0].text


  def result =
    """

<div id="map" style="height: 400px;"></div>

<script>
    var map = L.map('map').setView(${location}, 13);

    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
    }).addTo(map);

      var marker = L.marker(${location}).addTo(map)
    .bindPopup("${text}")
    .openPopup();

</script>

"""

  return result
}



static String createGoogleSearchLink(Object... keywords) {
  def baseUrl = "https://www.google.com/search?q="
  def query = keywords.collect { it.toString().replaceAll(" ", "+") }.join("+")
  def url = baseUrl + query
  return "<a href=\"${url}\" target=\"_blank\">Google it</a>"
}



static void createMoviePost(String key, Object value, String date, String slug) {

  if (value.post.description == null) {
    value.post.description = value.content.director + " (" + value.content.releaseYear + ")"
  }
  def postContent =
    """---
layout: post
slug: ${slug}
title: ${value.post.title}  |  ${value.post.type[1]}
date: ${date}
description: ${value.post.description}
director: ${value.content.director}
releaseYear: ${value.content.releaseYear}
image: assets/media/${key}/${value.media.picture}
text: media/${key}/${value.media.text}
pdf: ../../assets/media/${key}/${value.media.pdf}
tags: ${value.post.tags}
categories: ${value.post.categories}
youtube: ${value.media.youtube}

---

{% include  {{ page.text }} %}

{% if page.youtube %}
  {% youtube page.youtube %}
{% endif %}

{% unless page.pdf contains "null" %}
  {% pdf {{ page.pdf }} no_link %}
{% endunless %}

"""

    def postFile = new File("./_posts/${key}.md")
    postFile.text = postContent

  }

static void createSeriesPost(String key, Object value, String date, String slug) {

  if (value.post.description == null) {
    value.post.description = value.content.creator + " (" + value.content.releaseYear + ")"
  }

  def postContent =
      """---
layout: post
slug: ${slug}
title: ${value.post.title}  |  ${value.post.type[1]}
date: ${date}
description: ${value.post.description}
creator: ${value.content.creator}
releaseYear: ${value.content.releaseYear}
image: assets/media/${key}/${value.media.picture}
text: media/${key}/${value.media.text}
pdf: ../../assets/media/${key}/${value.media.pdf}
tags: ${value.post.tags}
categories: ${value.post.categories}
youtube: ${value.media.youtube}
---

{% include  {{ page.text }} %}

{% if page.youtube %}
  {% youtube page.youtube %}
{% endif %}

{% unless page.pdf contains "null" %}
  {% pdf {{ page.pdf }} no_link %}
{% endunless %}

"""

    def postFile = new File("./_posts/${key}.md")
    postFile.text = postContent

  }

static void createEventPost(String key, Object value, String date, String slug) {

  if (value.post.description == null) {
    value.post.description = ""
  }

  def program = new StringBuilder()
  program.append("**Programme** \r\r")
  value.content.each { item ->
    item.each { key2, value2 ->
      program.append("| ${value2} |")
    }
    program.append("\r")
  }

  def programString = program.toString()
  def googleSearchString = createGoogleSearchLink(value.post.title,
                                                         value.post.type[1],
                                                         value.post.description,
                                                         date)

  def description =
     value.post.description.size() != 0 ? value.post.description
                                        : "${value.author} (${value.place})"

  def postContent =
    """---
layout: post
slug: ${slug}
title: ${value.post.title}  |  ${value.post.type[1]}
date: ${date}
description: ${description}
image: assets/media/${key}/${value.media.picture}
text: media/${key}/${value.media.text}
pdf: "../../assets/media/${key}/${value.media.pdf}"
tags: ${value.post.tags}
categories: ${value.post.categories}
youtube: ${value.media.youtube}

---

${programString}

{% include  {{ page.text }} %}

${addPictures(key, value.media.addpictures ? true : false)}

{% if page.youtube %}
  {% youtube page.youtube %}
{% endif %}

{% unless page.pdf contains "null" %}
  {% pdf {{ page.pdf }} no_link %}
{% endunless %}

---

<div>
    <p style="text-align: left;"> ${googleSearchString} </p>
</div>

"""

  def postFile = new File("./_posts/${key}.md")
  postFile.text = postContent

}

static void createBookPost(String key, Object value, String date, String slug) {

  if (value.post.description == null) {
    value.post.description = "${value.content.author} (${value.content.published})"
  }

  def postContent =
    """---
layout: post
slug: ${slug}
title: ${value.post.title}  |  ${value.post.type[1]}
date: ${date}
description: ${value.post.description}
image: assets/media/${key}/${value.media.picture}
text: media/${key}/${value.media.text}
tags: ${value.post.tags}
categories: ${value.post.categories}

---

{% include  {{ page.text }} %}

"""

  def postFile = new File("./_posts/${key}.md")
  postFile.text = postContent

}

static void createExpoPost(String key, Object value, String date, String slug) {

  if (value.post.description == null) {
    value.post.description = "${value.content.city}"
  }

  def postContent =
    """---
layout: post
slug: ${slug}
title: ${value.post.title}  |  ${value.post.type[1]}
date: ${date}
description: ${value.post.description}
city: ${value.place}
image: assets/media/${key}/${value.media.picture}
text: media/${key}/${value.media.text}
tags: ${value.post.tags}
categories: ${value.post.categories}

---

{% include  {{ page.text }} %}

"""

  postContent += addPictures(key, value.media.addpictures ? true : false)

  if (value.map) {
    postContent += addMap(value.map)
  }

  def postFile = new File("./_posts/${key}.md")
  postFile.text = postContent

}

