package main

import (
  "fmt"
  "os/exec"
  //"strings"
  "bufio"
  "regexp"
  "os"
  "io/ioutil"
  "encoding/json"
)

var cmd_prep = "java -jar target/code-royale-1.0-SNAPSHOT-fat-tests.jar"
var game_json = ""

type jsonobject struct {
    Summaries []string `json:"summaries"`
    Score struct {
      Score_1 int `json:"0"`
      Score_2 int `json:"1"`
    } `json:"scores"`
}


func main() {
  gameCount := 10
  for i := 0; i < gameCount; i++ {
    GameRun(fmt.Sprintf("Game %d Start", i))
  }
}

func GameRun(message string) {
  fmt.Println(message)
  cmd := exec.Command("bash", "-c", cmd_prep)
  stdout, _ := cmd.StdoutPipe()
  cmd.Start()

  r := regexp.MustCompile(`Exposed web server dir: (\S+)`)
  scanner := bufio.NewScanner(stdout)

  for scanner.Scan() {
    m := scanner.Text()
    result := r.FindAllStringSubmatch(m, -1)
    if len(result) > 0 {
      web_dir := result[0][1]
      game_json = web_dir + "/game.json"
      fmt.Println("\tOutput dir: ", game_json)
      
      if err := cmd.Process.Kill(); err != nil {
        fmt.Println("\tfailed to kill process: ", err)
      }
    }
  }
  cmd.Wait()
  
  if game_json != "" {
    //game_json = "/Users/matscube/Dev/tmp/code-royale/config.json"
    file, e := ioutil.ReadFile(game_json)
    if e != nil {
        fmt.Printf("\tFile error: %v\n", e)
        os.Exit(1)
    }
    //fmt.Printf("%s\n", string(file))

    var jsontype jsonobject
    json.Unmarshal(file, &jsontype)
    //fmt.Printf("Results: %v\n", jsontype.Summaries)
    fmt.Printf("\tResults: %d : %d\n", jsontype.Score.Score_1, jsontype.Score.Score_2)
  }
  
  fmt.Println("\tfinish")
}
