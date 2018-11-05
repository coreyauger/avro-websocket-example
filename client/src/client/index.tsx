const avro = require('avsc');

/** A UUID.  Note this is not a */
export type UUID = string;
export type ProviderKey = string;

export const zeroId = "00000000-0000-0000-0000-000000000000"

export interface Model{
}

export interface Ingredient extends Model{
    name: string,
    sugar: number,
    fat: number    
}
 
export interface Pizza extends Model{
    name: string,
    ingredients: Ingredient[],
    vegetarian: boolean,
    vegan: boolean,
    calories: number
}


export interface EventMeta{
    eventId: string,
    eventType: string,
    source: string,
    correlationId?: string,
    userId?: string,
    socketId?: string,
    responseTo?: string,
    extra: any
}  

export interface SocketEvent{
    meta: EventMeta,
    payload: any[]
} 

//final case class Pizza(name: String, ingredients: Seq[Ingredient], vegetarian: Boolean, vegan: Boolean, calories: Int) extends Model

const pizza1 = {
  name: "yum yum",
  ingredients: [
      {name: "tomato", sugar: 10, fat: 2},
      {name: "cheese", sugar: 10, fat: 20}
  ],
  vegetarian: true,
  vegan: false,
  calories: 225 
} as Pizza


const pizzaType = avro.Type.forSchema({"type":"record","name":"Pizza","namespace":"m","fields":[{"name":"name","type":"string"},{"name":"ingredients","type":{"type":"array","items":{"type":"record","name":"Ingredient","fields":[{"name":"name","type":"string"},{"name":"sugar","type":"double"},{"name":"fat","type":"double"}]}}},{"name":"vegetarian","type":"boolean"},{"name":"vegan","type":"boolean"},{"name":"calories","type":"int"}]});
//const sType = avro.Type.forValue(sevent1);

const socketEventType = avro.Type.forSchema({"type":"record","name":"SocketEvent","namespace":"io.surfkit.typebus.event","fields":[{"name":"meta","type":{"type":"record","name":"EventMeta","fields":[{"name":"eventId","type":"string"},{"name":"eventType","type":"string"},{"name":"source","type":"string"},{"name":"correlationId","type":["null","string"]},{"name":"userId","type":["null","string"],"default":null},{"name":"socketId","type":["null","string"],"default":null},{"name":"responseTo","type":["null","string"],"default":null},{"name":"extra","type":{"type":"map","values":"string"},"default":{}}]}},{"name":"payload","type":"bytes"}]})


// We can use `type` to encode any values with the same structure:
const bufs = [
    pizzaType.toBuffer(pizza1)
];

console.log("bufsz: ", bufs)
console.log("back: ", pizzaType.fromBuffer(bufs[0]))

const socketEvent = {
    meta: {
        eventId: "123",
        eventType: "m.package.Pizza",
        source: "",
        correlationId: null,
        userId: null,
        socketId: "55",
        responseTo: null,
        extra: {}
    },
    payload: pizzaType.toBuffer(pizza1)
}

const bufs2 = [
    socketEventType.toBuffer(socketEvent)
];


console.log("bufsz: ", bufs2)
console.log("back: ", socketEventType.fromBuffer(bufs2[0]))


const ws = new WebSocket("ws://localhost:8181/v1/ws/56e32f7e-8350-4892-8c9c-0fd6faea36f8")
ws.onopen =  (event) => {
    console.log("WEBSOCKET IS CONNECTED !!!")
    //ws.send("Here's some text that the server is urgently awaiting!"); 
    ws.send(socketEventType.toBuffer(socketEvent))
};